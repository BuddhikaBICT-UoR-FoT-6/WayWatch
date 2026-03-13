package com.example.waywatch.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        // New (preferred) keys
        val preferredRoutesSet = stringSetPreferencesKey("preferred_routes")
        val watchedRoutesSet = stringSetPreferencesKey("watched_routes")

        val severityThreshold = doublePreferencesKey("severity_threshold")
        val refreshIntervalMinutes = intPreferencesKey("refresh_interval_minutes")
        val themeMode = stringPreferencesKey("theme_mode")

        // Legacy keys (kept for migration/backwards compatibility)
        val preferredRoutesCsvLegacy = stringPreferencesKey("preferred_routes_csv")
        val watchedRoutesCsvLegacy = stringPreferencesKey("watched_routes_csv")
        val lastNotifiedCsvLegacy = stringPreferencesKey("last_notified_route_window_csv")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val preferred =
            (prefs[Keys.preferredRoutesSet].sanitizeRouteSet())
                .ifEmpty { prefs.csvToSet(Keys.preferredRoutesCsvLegacy) }
        val watched =
            (prefs[Keys.watchedRoutesSet].sanitizeRouteSet())
                .ifEmpty { prefs.csvToSet(Keys.watchedRoutesCsvLegacy) }

        AppSettings(
            preferredRoutes = preferred,
            watchedRoutes = watched,
            severityThreshold = prefs[Keys.severityThreshold] ?: 4.0,
            refreshIntervalMinutes = prefs[Keys.refreshIntervalMinutes] ?: 15,
            themeMode = ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.DARK.name)
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setSeverityThreshold(threshold: Double) {
        context.settingsDataStore.edit { it[Keys.severityThreshold] = threshold }
    }

    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.refreshIntervalMinutes] = minutes }
    }

    suspend fun setPreferredRoutes(routes: Set<String>) {
        val sanitized = routes.sanitizeRouteSet()
        context.settingsDataStore.edit {
            it[Keys.preferredRoutesSet] = sanitized
            // Clear legacy once modern is set
            it.remove(Keys.preferredRoutesCsvLegacy)
        }
    }

    suspend fun setWatchedRoutes(routes: Set<String>) {
        val sanitized = routes.sanitizeRouteSet()
        context.settingsDataStore.edit {
            it[Keys.watchedRoutesSet] = sanitized
            it.remove(Keys.watchedRoutesCsvLegacy)
        }
    }

    /**
     * Dedupe storage: store per-route last-notified window kebabs as longPreferencesKey("last_notified_<route>").
     * This avoids parsing and makes updates atomic.
     */
    fun lastNotifiedRouteWindows(): Flow<Map<String, Long>> =
        context.settingsDataStore.data.map { prefs ->
            val fromPerRoute = prefs.asMap().entries.mapNotNull { entry ->
                val keyName = entry.key.name
                if (!keyName.startsWith(LAST_NOTIFIED_PREFIX)) return@mapNotNull null
                val routeId = keyName.removePrefix(LAST_NOTIFIED_PREFIX)
                val window = entry.value as? Long ?: return@mapNotNull null
                routeId to window
            }.toMap()

            if (fromPerRoute.isNotEmpty()) return@map fromPerRoute

            // Fallback: read legacy CSV map if per-route keys haven't been written yet.
            parseRouteWindowCsv(prefs[Keys.lastNotifiedCsvLegacy])
        }

    suspend fun setLastNotified(routeId: String, windowStartMs: Long) {
        val normalizedRoute = routeId.trim()
        if (normalizedRoute.isEmpty()) return

        context.settingsDataStore.edit { prefs ->
            prefs[lastNotifiedKeyFor(normalizedRoute)] = windowStartMs
            // Clear legacy once modern is set
            prefs.remove(Keys.lastNotifiedCsvLegacy)
        }
    }

    private fun lastNotifiedKeyFor(routeId: String) = longPreferencesKey("$LAST_NOTIFIED_PREFIX$routeId")

    private fun parseRouteWindowCsv(raw: String?): Map<String, Long> {
        return raw.orEmpty()
            .split(',')
            .mapNotNull { token ->
                val parts = token.split('=')
                if (parts.size != 2) return@mapNotNull null
                val route = parts[0].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val window = parts[1].trim().toLongOrNull() ?: return@mapNotNull null
                route to window
            }
            .toMap()
    }

    private fun Preferences.csvToSet(
        key: Preferences.Key<String>
    ): Set<String> {
        val raw = this[key].orEmpty()
        return raw.split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .toSet()
            .sanitizeRouteSet()
    }

    // Keep only ONE extension to avoid JVM signature clashes.
    // Use nullable receiver so callers can pass prefs[key] directly.
    private fun Set<String>?.sanitizeRouteSet(): Set<String> =
        this.orEmpty()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private companion object {
        const val LAST_NOTIFIED_PREFIX = "last_notified_"
    }
}

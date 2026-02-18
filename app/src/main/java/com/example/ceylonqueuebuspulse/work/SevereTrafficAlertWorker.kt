package com.example.ceylonqueuebuspulse.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ceylonqueuebuspulse.R
import com.example.ceylonqueuebuspulse.notifications.NotificationChannels
import com.example.ceylonqueuebuspulse.settings.SettingsRepository
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Locale

/**
 * Periodic background check for severe traffic on watched routes.
 *
 * Contract:
 * - Input: none (uses SettingsRepository)
 * - Output: posts a local notification if severity >= threshold and we've not notified for that route+window.
 */
class SevereTrafficAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val settingsRepo: SettingsRepository by inject()
    private val aggregationRepo: TrafficAggregationRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            NotificationChannels.ensureCreated(applicationContext)

            val settings = settingsRepo.settings.first()
            val watched = settings.watchedRoutes
            if (watched.isEmpty()) return Result.success()

            val threshold = settings.severityThreshold

            val nowMs = System.currentTimeMillis()
            val windowSizeMs = 15 * 60 * 1000L
            val windowStartMs = (nowMs / windowSizeMs) * windowSizeMs

            val lastNotified = settingsRepo.lastNotifiedRouteWindows().first()

            var notifiedCount = 0
            for (routeId in watched) {
                // Dedupe by window
                val lastWindow = lastNotified[routeId]
                if (lastWindow != null && lastWindow >= windowStartMs) continue

                val aggregates = aggregationRepo.observeAggregatedTraffic(routeId).first()
                val severityAvg = aggregates.firstOrNull()?.severityAvg ?: continue

                if (severityAvg >= threshold) {
                    if (canPostNotifications()) {
                        postNotification(routeId = routeId, severityAvg = severityAvg, windowStartMs = windowStartMs)
                    }
                    settingsRepo.setLastNotified(routeId, windowStartMs)
                    notifiedCount++
                }
            }

            // Keep worker successful; avoid retries for logic-level outcomes.
            Result.success()
        } catch (_: Exception) {
            // Network/DB errors: retry with WorkManager backoff.
            Result.retry()
        }
    }

    private fun canPostNotifications(): Boolean {
        // Android 13+ requires runtime permission; pre-33 doesn't.
        return if (android.os.Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun postNotification(routeId: String, severityAvg: Double, windowStartMs: Long) {
        // Explicit permission check for Android 13+; also guard with try/catch to satisfy lint.
        if (!canPostNotifications()) return

        val title = applicationContext.getString(R.string.notif_severe_title)
        val text = applicationContext.getString(
            R.string.notif_severe_body,
            routeId,
            String.format(Locale.US, "%.1f", severityAvg)
        )

        val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_SEVERE_TRAFFIC)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(applicationContext)
                .notify((routeId.hashCode() xor windowStartMs.hashCode()), notif)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight; ignore.
        }
    }
}

package com.example.ceylonqueuebuspulse.settings

/**
 * User-controlled settings for personalization and background notifications.
 */
data class AppSettings(
    val preferredRoutes: Set<String> = emptySet(),
    val watchedRoutes: Set<String> = emptySet(),
    val severityThreshold: Double = 4.0,
    /**
     * WorkManager periodic minimum is 15 minutes; UI may still allow finer granularity for manual refresh.
     */
    val refreshIntervalMinutes: Int = 15,
    val themeMode: ThemeMode = ThemeMode.DARK
)

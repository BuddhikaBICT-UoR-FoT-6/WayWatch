// Edited: 2026-01-08
// Purpose: Helper to schedule periodic SyncWorker and AggregationPlannerWorker with WorkManager (network required + backoff).

package com.example.ceylonqueuebuspulse.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ceylonqueuebuspulse.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private const val UNIQUE_PLANNER_WORK_NAME = "traffic_aggregation_planner_periodic"
    private const val UNIQUE_PLANNER_REFRESH_NAME = "traffic_aggregation_planner_refresh"
    private const val UNIQUE_SEVERE_ALERT_WORK_NAME = "severe_traffic_alerts_periodic"

    /**
     * Schedule periodic background sync and aggregation.
     * Interval is user-configurable (Settings.refreshIntervalMinutes) but WorkManager enforces min 15 minutes.
     */
    fun schedule(context: Context) {
        // Only run when the device has an active network connection.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val intervalMinutes = readIntervalMinutes(context)

        // Mongo-only: schedule the planner which enqueues one-time aggregation+sync workers.
        val plannerRequest = PeriodicWorkRequestBuilder<AggregationPlannerWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_PLANNER_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                plannerRequest
            )

        // Severe traffic alerts (watched routes)
        val alertRequest = PeriodicWorkRequestBuilder<SevereTrafficAlertWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.LINEAR,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                UNIQUE_SEVERE_ALERT_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                alertRequest
            )
    }

    private fun readIntervalMinutes(context: Context): Int {
        return try {
            runBlocking {
                val repo = SettingsRepository(context.applicationContext)
                val settings = repo.settings.first()
                settings.refreshIntervalMinutes.coerceAtLeast(15)
            }
        } catch (_: Throwable) {
            15
        }
    }

    /**
     * Trigger an immediate refresh of aggregation by enqueuing the planner as a unique one-time work.
     * Any existing pending planner refresh will be replaced.
     */
    fun refreshNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val refreshPlanner = OneTimeWorkRequestBuilder<AggregationPlannerWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_PLANNER_REFRESH_NAME,
                ExistingWorkPolicy.REPLACE,
                refreshPlanner
            )
    }
}

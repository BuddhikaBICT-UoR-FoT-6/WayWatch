// Edited: 2026-01-08
// Purpose: Planner worker that determines which route windows need aggregation and enqueues one-time workers for each.

package com.example.waywatch.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.waywatch.data.repository.TrafficAggregationRepository
import com.example.waywatch.work.MongoAggregationSyncWorker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Planner worker that runs periodically (every 15 minutes) to:
 * 1. Determine active routes and current time windows
 * 2. Enqueue one-time aggregation workers for each (routeId, windowStartMs) pair
 * 
 * This design pattern avoids the limitation that PeriodicWorker cannot receive input data.
 */
class AggregationPlannerWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val aggregationRepo: TrafficAggregationRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            val nowMs = System.currentTimeMillis()
            val windowSizeMs = 15 * 60 * 1000L // 15 minutes

            // Current window
            val currentWindowMs = floorToWindowStart(nowMs, windowSizeMs)
            // Previous window (for incremental catch-up)
            val previousWindowMs = currentWindowMs - windowSizeMs

            // TODO: In production, fetch active routes from a config/database/Firestore
            // For now, hardcode demo routes
            val activeRoutes = listOf("138", "174", "177", "120") 

            // Enqueue one-time workers for each route + window combination
            for (routeId in activeRoutes) {
                // Check if we've already synced this window
                val meta = aggregationRepo.getSyncMeta(routeId)
                val windows = mutableListOf(currentWindowMs)
                
                // If previous window wasn't synced yet, add it
                if (meta == null || (meta.lastWindowStartMs ?: 0L) < previousWindowMs) {
                    windows.add(0, previousWindowMs) // Sync older window first
                }

                for (windowStartMs in windows) {
                    enqueueAggregationWorker(routeId, windowStartMs)
                }
            }

            Result.success()
        } catch (e: Exception) {
            // Log error and retry
            Result.retry()
        }
    }

    private fun floorToWindowStart(tsMs: Long, windowSizeMs: Long): Long {
        if (windowSizeMs <= 0L) return tsMs
        return (tsMs / windowSizeMs) * windowSizeMs
    }

    private fun enqueueAggregationWorker(routeId: String, windowStartMs: Long) {
        val workRequest = OneTimeWorkRequestBuilder<MongoAggregationSyncWorker>()
            .setInputData(
                workDataOf(
                    "routeId" to routeId,
                    "windowStartMs" to windowStartMs
                )
            )
            .addTag("aggregation")
            .addTag("route_$routeId")
            .build()

        WorkManager.getInstance(applicationContext)
            .enqueue(workRequest)
    }
}

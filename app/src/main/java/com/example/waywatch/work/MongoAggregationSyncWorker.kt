// Edited: 2026-01-08
// Purpose: WorkManager worker that triggers aggregation and syncs aggregated result from the MongoDB backend.

package com.example.waywatch.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.waywatch.data.repository.TrafficAggregationRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MongoAggregationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val aggregationRepo: TrafficAggregationRepository by inject()

    override suspend fun doWork(): Result {
        val routeId = inputData.getString("routeId") ?: return Result.failure()
        val windowStartMs = inputData.getLong("windowStartMs", -1L)
        if (windowStartMs <= 0L) return Result.failure()

        return try {
            when (aggregationRepo.getAggregates(routeId)) {
                is com.example.waywatch.data.repository.AppResult.Ok -> Result.success()
                else -> Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("MongoAggregationSyncWorker", "❌ Aggregation sync failed", e)
            Result.retry()
        }
    }
}


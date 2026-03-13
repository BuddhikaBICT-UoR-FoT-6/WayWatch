package com.example.waywatch.data.repository

import com.example.waywatch.data.local.dao.AggregatedTrafficDao
import com.example.waywatch.data.local.dao.SyncMetaDao
import com.example.waywatch.data.local.entity.AggregatedTrafficEntity
import com.example.waywatch.data.local.entity.SyncMetaEntity
import com.example.waywatch.data.network.model.MongoApi
import com.example.waywatch.data.network.model.SubmitSampleRequest
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository managing traffic aggregation: submits user samples, syncs remote aggregates into Room,
 * and exposes aggregated data for UI (Room is source of truth).
 */
class TrafficAggregationRepository(
    private val mongoApi: MongoApi,
    private val aggregatedTrafficDao: AggregatedTrafficDao,
    private val syncMetaDao: SyncMetaDao,
) {
    companion object {
        private fun metaKey(routeId: String): String = "sync_route_$routeId"
    }

    // Simple in-memory cache for most recent window fetches (fast UI / reduces duplicate calls)
    private val windowCache = ConcurrentHashMap<String, List<AggregatedTrafficEntity>>()

    /**
     * Observe aggregated traffic data for a specific route, ordered by window (most recent first).
     */
    fun observeAggregatedTraffic(routeId: String): Flow<List<AggregatedTrafficEntity>> =
        aggregatedTrafficDao.observeAggregates(routeId)

    /** Get sync metadata for a specific route. */
    suspend fun getSyncMeta(routeId: String): SyncMetaEntity? = syncMetaDao.get(metaKey(routeId))

    /**
     * Submit a user sample to backend.
     */
    suspend fun submitUserSample(
        routeId: String,
        windowStartMs: Long,
        segmentId: String?,
        severity: Double,
        reportedAtMs: Long,
        userIdHash: String?
    ): AppResult<Unit> = try {
            val body = SubmitSampleRequest(
                routeId = routeId,
                windowStartMs = windowStartMs,
                segmentId = segmentId ?: "_all",
                severity = severity,
                reportedAtMs = reportedAtMs,
                userIdHash = userIdHash
            )
            val resp = mongoApi.submitSample(body)
            if (resp.ok) AppResult.Ok(Unit)
            else AppResult.Err(AppError.Server(resp.error ?: resp.message ?: "Submit failed"))
        } catch (t: Throwable) {
            AppResult.Err(RepositoryErrorMapper.toAppError(t))
        }

    /**
     * Fetch latest aggregates from remote and upsert into Room.
     * Room remains the source of truth.
     */
    suspend fun getAggregates(routeId: String? = null): AppResult<List<com.example.waywatch.data.network.model.AggregateDto>> =
        try {
                val resp = mongoApi.getAggregates(routeId = routeId)
                if (!resp.ok) {
                    return AppResult.Err(AppError.Server(resp.error ?: resp.message ?: "Sync failed"))
                }

                val dtos = resp.data ?: emptyList()
                val entities = dtos.map { dto ->
                    AggregatedTrafficEntity(
                        routeId = dto.routeId,
                        windowStartMs = dto.windowStartMs,
                        segmentId = dto.segmentId,
                        severityAvg = dto.severityAvg,
                        severityP50 = dto.severityP50,
                        severityP90 = dto.severityP90,
                        sampleCount = dto.sampleCount,
                        lastAggregatedAtMs = dto.lastAggregatedAtMs
                    )
                }

                if (entities.isNotEmpty()) {
                    // Group by routeId and windowStartMs to overwrite correctly, or just insert them.
                    // For simplicity, we can insert or update them all.
                    // Since overwriteWindow requires specific routeId and windowStartMs, we just insert them all.
                    // Assuming we have an insertAll method, wait, we have overwriteWindow.
                    // If we fetch all, we should iterate and overwrite per window or just use the DAO to upsert.
                    // Let's use aggregatedTrafficDao.overwriteWindow for each group:
                    entities.groupBy { it.routeId to it.windowStartMs }.forEach { (key, group) ->
                        aggregatedTrafficDao.overwriteWindow(key.first, key.second, group)
                    }
                    if (routeId != null) {
                        syncMetaDao.upsert(
                            SyncMetaEntity(
                                key = metaKey(routeId),
                                lastSyncAtMs = System.currentTimeMillis(),
                                lastWindowStartMs = entities.maxOfOrNull { it.windowStartMs } ?: 0L
                            )
                        )
                    }
                }
                
                AppResult.Ok(dtos)
            } catch (t: Throwable) {
                AppResult.Err(RepositoryErrorMapper.toAppError(t))
            }
}

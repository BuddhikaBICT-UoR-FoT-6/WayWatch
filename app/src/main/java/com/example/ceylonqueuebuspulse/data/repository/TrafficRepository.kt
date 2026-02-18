// Edited: 2026-01-06
// Purpose: Repository managing traffic reports; persists to Room via DAO, maps entities to domain,
//          and exposes reactive Flows for UI/ViewModel. Extended for remote sync + best-effort push.

package com.example.ceylonqueuebuspulse.data.repository

// --- Domain models and helpers ---
import com.example.ceylonqueuebuspulse.data.LatLng
import com.example.ceylonqueuebuspulse.data.TrafficReport
import com.example.ceylonqueuebuspulse.data.TrafficSource
import com.example.ceylonqueuebuspulse.data.UserLocationUpdate

// --- Android/DI ---
import android.content.Context

// --- Coroutines + Flow ---
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// --- Networking (Mongo API) ---
import com.example.ceylonqueuebuspulse.data.network.model.MongoApi
import com.example.ceylonqueuebuspulse.data.network.model.SubmitSampleRequest

// --- Room DAO + entity (local persistence) ---
import com.example.ceylonqueuebuspulse.data.local.TrafficReportDao
import com.example.ceylonqueuebuspulse.data.local.TrafficReportEntity

/**
 * Repository that acts as the single source of truth for traffic reports.
 *
 * Responsibilities:
 * - Read: Observe Room (DAO) as a Flow of entities and map them to domain models.
 * - Write: Seed historical reports and append user-submitted reports into Room.
 * - Remote: Sync remote -> Room and push user updates (best-effort).
 */
class TrafficRepository(
    // DAO dependency; provided by the Room database.
    private val dao: TrafficReportDao,
    // IO dispatcher for Room and network work.
    private val io: CoroutineDispatcher = Dispatchers.IO,
    // Application context (reserved for future needs; e.g., resources, connectivity)
    @Suppress("unused") private val appContext: Context,
    private val mongoApi: MongoApi,
    // Optional batcher for sending samples efficiently
    private val sampleBatcher: com.example.ceylonqueuebuspulse.data.network.SampleBatcher? = null
) {

    // Stream of domain models consumed by ViewModel/UI. Mapping preserves reactivity from Room.
    val reports: Flow<List<TrafficReport>> =
        dao.observeReports().map { entities -> entities.map { it.toDomain() } }

    /**
     * Replace all existing reports with the provided historical samples.
     * Typically used during bootstrap or periodic data refresh.
     */
    suspend fun seedHistoricalData(sample: List<TrafficReport>) = withContext(io) {
        val entities = sample.map { it.toEntity() }
        dao.clearAll()
        dao.insertReports(entities)
    }

    /**
     * Convert a user location update into a traffic report and persist it locally.
     * Then best-effort push the update to the backend (fire-and-forget).
     */
    suspend fun submitUserUpdate(update: UserLocationUpdate) = withContext(io) {
        val nowMs = System.currentTimeMillis()
        val routeId = update.routeId ?: "unknown"

        val report = TrafficReport(
            id = "user-${update.id}",
            routeId = routeId,
            severity = 3, // TODO: derive from heuristics
            segment = listOf(LatLng(update.lat, update.lng)),
            timestamp = nowMs,
            source = TrafficSource.USER
        )
        // Persist locally; UI will react via Room Flow
        dao.insertReport(report.toEntity())

        // Compute window start inline (avoid unused-parameter inspection)
        val windowSizeMs = 15 * 60 * 1000L
        val windowStartMs = (nowMs / windowSizeMs) * windowSizeMs

        val sample = SubmitSampleRequest(
            routeId = routeId,
            windowStartMs = windowStartMs,
            segmentId = "_all",
            severity = report.severity.toDouble(),
            reportedAtMs = nowMs,
            userIdHash = update.userId
        )

        // Submit sample via batcher if available, otherwise fall back to direct API call
        if (sampleBatcher != null) {
            sampleBatcher.submit(sample)
        } else {
            runCatching {
                mongoApi.submitSample(sample)
            }
        }

        // Server-side aggregation will run on backend; no client aggregation here.
    }

    // --- Mapping helpers: Entity <-> Domain ---

    /** Map a persistence entity into a domain model. */
    private fun TrafficReportEntity.toDomain(): TrafficReport =
        TrafficReport(
            id = id.toString(), // convert auto-generated Long id to String for domain
            routeId = routeId,
            severity = severity,
            source = TrafficSource.valueOf(source),
            segment = emptyList(), // segments not persisted in v1
            timestamp = timestampMs
        )

    /** Map a domain model into a persistence entity. */
    private fun TrafficReport.toEntity(): TrafficReportEntity =
        TrafficReportEntity(
            routeId = routeId,
            severity = severity,
            source = source.name,
            timestampMs = timestamp
        )
}

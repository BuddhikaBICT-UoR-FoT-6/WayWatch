// Edited: 2026-01-06
// Purpose: Repository managing traffic reports; persists to Room via DAO, maps entities to domain,
//          and exposes reactive Flows for UI/ViewModel. Extended for remote sync + best-effort push.

package com.example.waywatch.data.repository

// --- Domain models and helpers ---
import com.example.waywatch.data.LatLng
import com.example.waywatch.data.TrafficReport
import com.example.waywatch.data.TrafficSource
import com.example.waywatch.data.UserLocationUpdate

// --- Android/DI ---
import android.content.Context

// --- Coroutines + Flow ---
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// --- Networking (Mongo API) ---
import com.example.waywatch.data.network.model.MongoApi
import com.example.waywatch.data.network.model.SubmitSampleRequest

// --- Room DAO + entity (local persistence) ---
import com.example.waywatch.data.local.TrafficReportDao
import com.example.waywatch.data.local.TrafficReportEntity

/**
 * Repository that acts as the single source of truth for traffic reports.
 *
 * Responsibilities:
 * - Read: Observe Room (DAO) as a Flow of entities and map them to domain models.
 * - Write: Seed historical reports and append user-submitted reports into Room.
 * - Remote: Push user updates (best-effort).
 */
class TrafficRepository(
    // DAO dependency; provided by the Room database.
    private val dao: TrafficReportDao,
    // Application context (reserved for future needs; e.g., resources, connectivity)
    @Suppress("unused") private val appContext: Context,
    private val mongoApi: MongoApi,
    // Optional batcher for sending samples efficiently
    private val sampleBatcher: com.example.waywatch.data.network.SampleBatcher? = null,
    // List of data sources for fetching traffic
    private val sources: List<com.example.waywatch.data.source.TrafficDataSource> = emptyList()
) : ITrafficReader, ILocationSubmitter {

    // In-memory cache of last emitted list (UI can render instantly while Room catches up)
    @Volatile
    private var lastReportsCache: List<TrafficReport> = emptyList()

    // Stream of domain models consumed by ViewModel/UI. Mapping preserves reactivity from Room.
    override val reports: Flow<List<TrafficReport>> =
        dao.observeReports().map { entities ->
            val mapped = entities.map { it.toDomain() }
            lastReportsCache = mapped
            mapped
        }

    /** Get the latest cached reports (bypassing Room). */
    override fun getCachedReports(): List<TrafficReport> = lastReportsCache

    /**
     * Aggregates traffic from all registered sources for a given bounding box.
     */
    fun fetchTrafficFromSources(bbox: com.example.waywatch.data.source.BBox): Flow<List<com.example.waywatch.data.TrafficSample>> {
        if (sources.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val flows = sources.map { it.fetchTraffic(bbox) }
        return kotlinx.coroutines.flow.combine(flows) { lists ->
            lists.flatMap { it.toList() }
        }
    }

    /**
     * Replace all existing reports with the provided historical samples.
     * Typically used during bootstrap or periodic data refresh.
     */
    suspend fun seedHistoricalData(sample: List<TrafficReport>): AppResult<Unit> = try {
            val entities = sample.map { it.toEntity() }
            dao.clearAll()
            dao.insertReports(entities)
            lastReportsCache = sample
            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(RepositoryErrorMapper.toAppError(t))
        }

    /**
     * Convert a user location update into a traffic report and persist it locally.
     * Then best-effort push the update to the backend.
     */
    override suspend fun submitUserUpdate(update: UserLocationUpdate): AppResult<Unit> = try {
            val nowMs = System.currentTimeMillis()
            val routeId = update.routeId ?: "unknown"

            val report = TrafficReport(
                id = "user-${update.id}",
                routeId = routeId,
                severity = 3, // TODO: derive from heuristics / provider
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

            // Fire-and-forget: don't fail UI just because push fails; still return OK.
            runCatching {
                if (sampleBatcher != null) sampleBatcher.submit(sample) else mongoApi.submitSample(sample)
            }

            AppResult.Ok(Unit)
        } catch (t: Throwable) {
            AppResult.Err(RepositoryErrorMapper.toAppError(t))
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

package com.example.waywatch.data.network.model

import retrofit2.http.*

// DTOs matching the Node server responses

data class SubmitSampleRequest(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String = "_all",
    val severity: Double,
    val reportedAtMs: Long,
    val userIdHash: String? = null
)

data class AggregateDto(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String,
    val severityAvg: Double,
    val severityP50: Double?,
    val severityP90: Double?,
    val sampleCount: Int,
    val lastAggregatedAtMs: Long
)

data class ApiResponse<T>(
    val ok: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

interface MongoApi {
    @POST("api/v1/samples")
    suspend fun submitSample(@Body body: SubmitSampleRequest): ApiResponse<Map<String, Any>>

    @POST("api/v1/samples")
    suspend fun submitSamples(@Body body: List<SubmitSampleRequest>): ApiResponse<Map<String, Any>>

    @GET("api/v1/aggregates")
    suspend fun getAggregates(
        @Query("routeId") routeId: String? = null,
        @Query("windowStartMs") windowStartMs: Long? = null
    ): ApiResponse<List<AggregateDto>>

    @GET("api/v1/auth/sessions")
    suspend fun getSessions(): com.example.waywatch.data.network.model.SessionsResponse

    @DELETE("api/v1/auth/sessions/{deviceId}")
    suspend fun revokeSession(@Path("deviceId") deviceId: String): ApiResponse<Unit>
}

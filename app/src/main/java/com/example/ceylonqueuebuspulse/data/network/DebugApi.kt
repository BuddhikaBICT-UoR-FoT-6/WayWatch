package com.example.ceylonqueuebuspulse.data.network

import com.example.ceylonqueuebuspulse.data.network.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class RoutePointsResponse(
    val routeId: String,
    val points: List<RoutePoint>
)

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val severity: Double? = null
)

interface DebugApi {
    // GET /api/v1/debug/provider/point?lat=...&lon=...
    @GET("api/v1/debug/provider/point")
    suspend fun providerPoint(@Query("lat") lat: Double, @Query("lon") lon: Double): ApiResponse<Map<String, Any>>

    // GET /api/v1/routes/{routeId}/points?maxPoints=12&windowStartMs=...
    @GET("api/v1/routes/{routeId}/points")
    suspend fun routePoints(
        @Path("routeId") routeId: String,
        @Query("maxPoints") maxPoints: Int = 12,
        @Query("windowStartMs") windowStartMs: Long? = null
    ): ApiResponse<RoutePointsResponse>
}

package com.example.waywatch.data.network

import com.example.waywatch.data.network.model.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Backend wrapper for OSM/Overpass-derived bus route discovery.
 */
interface OsmRoutesApi {

    data class RouteSummaryDto(
        val ref: String,
        val name: String? = null,
        val id: Long
    )

    @GET("api/v1/osm/routes")
    suspend fun searchByRef(
        @Query("q") q: String,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<RouteSummaryDto>>

    @GET("api/v1/osm/routes/nearby")
    suspend fun nearby(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
        @Query("limit") limit: Int = 20
    ): ApiResponse<List<RouteSummaryDto>>

    @GET("api/v1/osm/routes/{ref}/geojson")
    suspend fun geoJson(
        @Path("ref") ref: String
    ): ApiResponse<Any>
}


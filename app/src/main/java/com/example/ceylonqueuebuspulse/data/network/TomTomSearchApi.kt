package com.example.ceylonqueuebuspulse.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Partial response model for TomTom Search API (forward geocoding)

data class TomTomSearchResponse(
    val query: String? = null,
    val results: List<TomTomSearchResult> = emptyList()
)

data class TomTomSearchResult(
    val id: String? = null,
    val address: TomTomAddress? = null,
    val position: TomTomPosition? = null
)

data class TomTomAddress(
    val freeformAddress: String? = null,
    val municipality: String? = null,
    val country: String? = null
)

data class TomTomPosition(
    val lat: Double? = null,
    val lon: Double? = null
)

interface TomTomSearchApi {
    // Example: https://api.tomtom.com/search/2/search/{query}.json?key=YOUR_KEY
    @GET("search/2/search/{query}.json")
    suspend fun search(
        @Path("query") query: String,
        @Query("key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): TomTomSearchResponse
}

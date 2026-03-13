package com.example.waywatch.traffic

import com.example.waywatch.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class ProviderResponse(val ok: Boolean, val mapped: Map<String, Any>?, val provider: Map<String, Any>?)
data class PostResult(val ok: Boolean, val inserted: Int)

interface BackendApi {
    @GET("api/v1/debug/provider/point")
    suspend fun getProviderPoint(@Query("lat") lat: Double, @Query("lon") lon: Double): ProviderResponse

    @POST("traffic/samples")
    suspend fun postSamples(@Body body: List<Map<String, Any>>): PostResult
}

object ApiClient {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.MONGO_API_BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: BackendApi = retrofit.create(BackendApi::class.java)
}

package com.example.ceylonqueuebuspulse.data.auth

import com.example.ceylonqueuebuspulse.data.network.model.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: AuthRequest): ApiResponse<AuthResponseDto>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: AuthRequest): ApiResponse<AuthResponseDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiResponse<RefreshResponseDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(): ApiResponse<Map<String, Any>>

    @GET("api/v1/auth/me")
    suspend fun me(): ApiResponse<Map<String, Any>>
}

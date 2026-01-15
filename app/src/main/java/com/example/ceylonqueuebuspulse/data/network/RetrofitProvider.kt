// Edited: 2026-01-05
// Purpose: Provide a Retrofit singleton configured with Moshi for JSON parsing.

package com.example.ceylonqueuebuspulse.data.network

import com.example.ceylonqueuebuspulse.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.example.ceylonqueuebuspulse.data.auth.AuthInterceptor
import com.example.ceylonqueuebuspulse.data.auth.AuthRepository
import com.example.ceylonqueuebuspulse.data.auth.TokenStore
import com.example.ceylonqueuebuspulse.data.auth.TokenRefreshAuthenticator
import okhttp3.logging.HttpLoggingInterceptor

// Simple Retrofit provider. Replace BASE_URL with your backend URL.
object RetrofitProvider {
    private val mongoApiBaseUrl: String get() = BuildConfig.MONGO_API_BASE_URL

    // Build a Moshi instance for Kotlin JSON serialization/deserialization.
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    fun mongoRetrofit(tokenStore: TokenStore? = null, authRepository: AuthRepository? = null): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)

        if (tokenStore != null) {
            clientBuilder.addInterceptor(AuthInterceptor(tokenStore))
        }

        if (tokenStore != null && authRepository != null) {
            clientBuilder.authenticator(TokenRefreshAuthenticator(authRepository, tokenStore))
        }

        return Retrofit.Builder()
            .baseUrl(mongoApiBaseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(clientBuilder.build())
            .build()
    }
}

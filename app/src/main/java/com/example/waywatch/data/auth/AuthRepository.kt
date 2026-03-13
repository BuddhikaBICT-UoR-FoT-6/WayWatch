package com.example.waywatch.data.auth

// Secure token storage and JWT lifecycle management for authentication
// Uses EncryptedSharedPreferences for secure storage
// Requires: implementation("androidx.security:security-crypto:1.1.0-alpha06") in build.gradle.kts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.Context
import android.content.SharedPreferences

class AuthRepository(
    private val api: AuthApi, // API client for auth endpoints
    private val tokenStore: TokenStore, // Abstraction for token flows
    context: Context
) {
    // MasterKey for encryption
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // EncryptedSharedPreferences for secure token storage
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Flows for observing tokens
    val accessToken: Flow<String?> = tokenStore.accessTokenFlow
    val refreshToken: Flow<String?> = tokenStore.refreshTokenFlow

    // Check if user is logged in
    suspend fun isLoggedIn(): Boolean = accessToken.first()?.isNotBlank() == true

    // Register a new user and store tokens
    suspend fun register(email: String, password: String): Result<AuthResponseDto> {
        return runCatching {
            val res = api.register(AuthRequest(email = email.trim(), password = password))
            if (!res.ok || res.data == null) error(res.error ?: "Registration failed")
            tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
            res.data
        }
    }

    // Login and store tokens
    suspend fun login(email: String, password: String): Result<AuthResponseDto> {
        return runCatching {
            val res = api.login(AuthRequest(email = email.trim(), password = password))
            if (!res.ok || res.data == null) error(res.error ?: "Login failed")
            tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
            res.data
        }
    }

    /**
     * Attempt to refresh tokens; returns true if a new access token was stored.
     */
    suspend fun refresh(): Boolean {
        val rt = refreshToken.first() ?: return false
        val res = api.refresh(RefreshRequest(refreshToken = rt))
        if (!res.ok || res.data == null) return false
        tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
        return true
    }

    // Logout and clear tokens
    suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }

    // Save tokens securely
    fun saveTokens(jwt: String, refreshToken: String) {
        prefs.edit().putString("jwt", jwt).putString("refresh", refreshToken).apply()
    }

    // Get JWT from secure storage
    fun getJwt(): String? = prefs.getString("jwt", null)
    
    // Extract userId from JWT payload
    fun getUserId(): String? {
        val jwt = getJwt() ?: return null
        return try {
            val parts = jwt.split(".")
            if (parts.size != 3) return null
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            val json = org.json.JSONObject(payload)
            json.optString("id").takeIf { it.isNotBlank() } ?: json.optString("sub").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    // Get refresh token from secure storage
    fun getRefreshToken(): String? = prefs.getString("refresh", null)

    /**
     * Call this when JWT is expired. Attempts to refresh JWT using refresh token.
     * Returns new JWT if successful, null otherwise.
     */
    suspend fun refreshJwt(): String? {
        val rt = getRefreshToken() ?: return null
        // Use your API client to POST /refresh with { refreshToken: ... }
        val response = api.refresh(RefreshRequest(refreshToken = rt))
        if (response.ok && response.data != null) {
            val newJwt = response.data.accessToken
            val newRefresh = response.data.refreshToken
            if (newJwt is String && newRefresh is String) {
                saveTokens(newJwt, newRefresh)
                return newJwt
            }
        }
        return null
    }
}

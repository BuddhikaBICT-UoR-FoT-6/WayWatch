package com.example.ceylonqueuebuspulse.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val api: AuthApi,
    private val tokenStore: TokenStore
) {
    val accessToken: Flow<String?> = tokenStore.accessTokenFlow
    val refreshToken: Flow<String?> = tokenStore.refreshTokenFlow

    suspend fun isLoggedIn(): Boolean = accessToken.first()?.isNotBlank() == true

    suspend fun register(email: String, password: String): Result<AuthResponseDto> {
        return runCatching {
            val res = api.register(AuthRequest(email = email.trim(), password = password))
            if (!res.ok || res.data == null) error(res.error ?: "Registration failed")
            tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
            res.data
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponseDto> {
        return runCatching {
            val res = api.login(AuthRequest(email = email.trim(), password = password))
            if (!res.ok || res.data == null) error(res.error ?: "Login failed")
            tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
            res.data
        }
    }

    /** Attempt to refresh tokens; returns true if a new access token was stored. */
    suspend fun refresh(): Boolean {
        val rt = refreshToken.first() ?: return false
        val res = api.refresh(RefreshRequest(refreshToken = rt))
        if (!res.ok || res.data == null) return false
        tokenStore.setTokens(res.data.accessToken, res.data.refreshToken)
        return true
    }

    suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }
}

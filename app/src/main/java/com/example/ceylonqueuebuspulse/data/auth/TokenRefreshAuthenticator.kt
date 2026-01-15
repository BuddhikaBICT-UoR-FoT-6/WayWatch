package com.example.ceylonqueuebuspulse.data.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Automatically refreshes the access token when the server returns 401.
 *
 * Strategy:
 * - If we have no refresh token -> give up.
 * - Call AuthRepository.refresh() (which rotates refresh token too).
 * - Retry the original request with the new access token.
 */
class TokenRefreshAuthenticator(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite loops.
        if (responseCount(response) >= 2) return null

        return runBlocking {
            val refreshToken = tokenStore.refreshTokenFlow.first()
            if (refreshToken.isNullOrBlank()) return@runBlocking null

            val refreshed = runCatching { authRepository.refresh() }.getOrDefault(false)
            if (!refreshed) return@runBlocking null

            val newAccessToken = tokenStore.accessTokenFlow.first()
            if (newAccessToken.isNullOrBlank()) return@runBlocking null

            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response
        var count = 1
        while (r?.priorResponse != null) {
            count++
            r = r.priorResponse
        }
        return count
    }
}


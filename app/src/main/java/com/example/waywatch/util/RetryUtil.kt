// Edited: 2026-01-08
// Purpose: Retry policy and exponential backoff utilities for network operations

package com.example.waywatch.util

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Configuration for retry behavior with exponential backoff.
 */
data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
) {
    companion object {
        val DEFAULT = RetryPolicy()
        val AGGRESSIVE = RetryPolicy(maxAttempts = 5, initialDelayMs = 500L)
        val CONSERVATIVE = RetryPolicy(maxAttempts = 2, initialDelayMs = 2000L)
    }
}

/**
 * Result wrapper for operations that can fail.
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable, val message: String) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

/**
 * Network-related exceptions with user-friendly messages.
 */
sealed class NetworkException(message: String) : Exception(message) {
    class NoConnection : NetworkException("No internet connection. Please check your network.")
    class Timeout : NetworkException("Request timed out. Please try again.")
    class ServerError : NetworkException("Server error. Please try again later.")
    class Unauthorized : NetworkException("Authentication required. Please sign in.")
    class PermissionDenied : NetworkException("Access denied. Check your permissions.")
    class NotFound : NetworkException("Resource not found.")
    class Unknown(msg: String) : NetworkException(msg)
}

/**
 * Utility object for retry logic with exponential backoff.
 */
object RetryUtil {

    /**
     * Execute a suspending operation with retry logic and exponential backoff.
     *
     * @param policy Retry configuration
     * @param block The operation to execute
     * @return Result of the operation
     */
    suspend fun <T> withRetry(
        policy: RetryPolicy = RetryPolicy.DEFAULT,
        block: suspend (attempt: Int) -> T
    ): T {
        var currentDelay = policy.initialDelayMs
        var lastException: Exception? = null

        repeat(policy.maxAttempts) { attempt ->
            try {
                return block(attempt + 1)
            } catch (e: Exception) {
                lastException = e

                // Don't retry on certain exceptions
                if (!shouldRetry(e)) {
                    throw e
                }

                // Last attempt - don't delay
                if (attempt == policy.maxAttempts - 1) {
                    throw e
                }

                // Calculate delay with jitter
                val jitter = currentDelay * policy.jitterFactor * (Math.random() * 2 - 1)
                val delayWithJitter = (currentDelay + jitter).toLong()

                android.util.Log.w(
                    "RetryUtil",
                    "Attempt ${attempt + 1}/${policy.maxAttempts} failed: ${e.message}. " +
                    "Retrying in ${delayWithJitter}ms..."
                )

                delay(delayWithJitter)

                // Exponential backoff
                currentDelay = min(
                    (currentDelay * policy.backoffMultiplier).toLong(),
                    policy.maxDelayMs
                )
            }
        }

        throw lastException ?: Exception("Unknown error")
    }

    /**
     * Determine if an exception is retryable.
     */
    private fun shouldRetry(exception: Exception): Boolean {
        return when (exception) {
            is NetworkException.NoConnection -> true
            is NetworkException.Timeout -> true
            is NetworkException.ServerError -> true
            is NetworkException.Unauthorized -> false
            is NetworkException.PermissionDenied -> false
            is NetworkException.NotFound -> false
            is java.io.IOException -> true
            is java.net.SocketTimeoutException -> true
            else -> false
        }
    }

    /**
     * Convert exceptions to user-friendly NetworkException.
     */
    fun mapException(exception: Throwable): NetworkException {
        return when (exception) {
            is NetworkException -> exception
            is java.net.UnknownHostException ->
                NetworkException.Unknown("Cannot reach server host. Please verify your connection and server URL.")
            is java.net.ConnectException ->
                NetworkException.Unknown("Cannot connect to server. Please verify your connection and server status.")
            is java.net.SocketTimeoutException -> NetworkException.Timeout()
            is java.io.IOException -> NetworkException.NoConnection()
            else -> NetworkException.Unknown(exception.message ?: "Unknown error occurred")
        }
    }
}

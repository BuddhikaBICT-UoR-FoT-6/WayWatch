package com.example.waywatch.data.repository

/**
 * UI-friendly error type returned by repositories.
 * Keep this lightweight and stable; map low-level exceptions here.
 */
sealed class AppError(open val userMessage: String) {
    data class Network(override val userMessage: String = "No internet connection") : AppError(userMessage)
    data class Timeout(override val userMessage: String = "Request timed out") : AppError(userMessage)
    data class Unauthorized(override val userMessage: String = "Authentication required") : AppError(userMessage)
    data class Server(override val userMessage: String = "Server error") : AppError(userMessage)
    data class Parse(override val userMessage: String = "Bad response from server") : AppError(userMessage)
    data class Unknown(override val userMessage: String = "Something went wrong") : AppError(userMessage)
}

/** Success/failure wrapper used at repository boundaries. */
sealed class AppResult<out T> {
    data class Ok<T>(val data: T) : AppResult<T>()
    data class Err(val error: AppError) : AppResult<Nothing>()
}

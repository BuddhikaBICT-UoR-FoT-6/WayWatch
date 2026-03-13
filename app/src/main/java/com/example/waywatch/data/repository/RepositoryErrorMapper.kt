package com.example.waywatch.data.repository

import com.example.waywatch.util.NetworkException
import com.example.waywatch.util.RetryUtil
import com.squareup.moshi.JsonDataException
import retrofit2.HttpException

object RepositoryErrorMapper {
    fun toAppError(t: Throwable): AppError {
        return when (t) {
            is JsonDataException -> AppError.Parse(t.message ?: AppError.Parse().userMessage)
            is HttpException -> {
                when (t.code()) {
                    401 -> AppError.Unauthorized()
                    in 500..599 -> AppError.Server()
                    else -> AppError.Unknown(t.message())
                }
            }
            else -> {
                when (val mapped = RetryUtil.mapException(t)) {
                    is NetworkException.NoConnection -> AppError.Network(mapped.message ?: AppError.Network().userMessage)
                    is NetworkException.Timeout -> AppError.Timeout(mapped.message ?: AppError.Timeout().userMessage)
                    is NetworkException.Unauthorized -> AppError.Unauthorized(mapped.message ?: AppError.Unauthorized().userMessage)
                    is NetworkException.ServerError -> AppError.Server(mapped.message ?: AppError.Server().userMessage)
                    is NetworkException -> AppError.Unknown(mapped.message ?: AppError.Unknown().userMessage)
                }
            }
        }
    }
}

package com.example.waywatch.data.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SessionDto(
    @Json(name = "deviceId") val deviceId: String,
    @Json(name = "deviceName") val deviceName: String?,
    @Json(name = "issuedAt") val issuedAt: String,
    @Json(name = "lastUsedAt") val lastUsedAt: String?
)

@JsonClass(generateAdapter = true)
data class SessionsResponse(
    @Json(name = "ok") val ok: Boolean,
    @Json(name = "data") val data: List<SessionDto>?
)

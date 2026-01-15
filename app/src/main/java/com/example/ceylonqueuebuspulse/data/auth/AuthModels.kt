package com.example.ceylonqueuebuspulse.data.auth

data class AuthRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class RefreshResponseDto(
    val accessToken: String,
    val refreshToken: String
)

data class AuthUserDto(
    val id: String,
    val email: String,
    val role: String
)

data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUserDto
)

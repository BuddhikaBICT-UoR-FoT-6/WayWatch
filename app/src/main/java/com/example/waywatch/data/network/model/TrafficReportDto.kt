package com.example.waywatch.data.network.model

// DTO used for JSON parsing in tests and API responses
data class TrafficReportDto(
    val id: String,
    val route: String,
    val severity: Int,
    val points: Int,
    val updatedAt: Long
)

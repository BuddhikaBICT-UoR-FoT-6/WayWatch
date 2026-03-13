package com.example.waywatch.data

data class TrafficSample(
    val id: String,
    val routeId: String,
    val severity: Int,
    val timestamp: Long,
    val location: LatLng,
    val source: String
)

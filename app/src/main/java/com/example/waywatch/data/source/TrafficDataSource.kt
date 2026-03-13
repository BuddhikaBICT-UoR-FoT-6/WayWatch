package com.example.waywatch.data.source

import com.example.waywatch.data.TrafficSample
import kotlinx.coroutines.flow.Flow

data class BBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
)

interface TrafficDataSource {
    fun fetchTraffic(bbox: BBox): Flow<List<TrafficSample>>
}

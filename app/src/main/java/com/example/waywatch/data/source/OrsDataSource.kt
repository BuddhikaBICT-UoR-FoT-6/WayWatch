package com.example.waywatch.data.source

import com.example.waywatch.data.TrafficSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class OrsDataSource : TrafficDataSource {
    override fun fetchTraffic(bbox: BBox): Flow<List<TrafficSample>> {
        // Implementation for OpenRouteService API would go here
        return flowOf(emptyList())
    }
}

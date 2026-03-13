package com.example.waywatch.data.repository

import com.example.waywatch.data.TrafficReport
import kotlinx.coroutines.flow.Flow

interface ITrafficReader {
    val reports: Flow<List<TrafficReport>>
    fun getCachedReports(): List<TrafficReport>
}

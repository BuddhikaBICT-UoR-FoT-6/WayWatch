package com.example.waywatch.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity representing aggregated traffic data for a route segment within a time window.
 */
@Entity(
    tableName = "aggregated_traffic",
    primaryKeys = ["routeId", "windowStartMs", "segmentId"],
    indices = [Index(value = ["routeId"]), Index(value = ["windowStartMs"])]
)

data class AggregatedTrafficEntity(
    val routeId: String,
    val windowStartMs: Long,
    val segmentId: String,
    val severityAvg: Double,
    val severityP50: Double?,
    val severityP90: Double?,
    val sampleCount: Int,
    val lastAggregatedAtMs: Long
)
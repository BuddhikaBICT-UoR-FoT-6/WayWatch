// Edited: 2026-01-05
// Purpose: Room entity schema for persisted traffic reports used by the repository/UI flows.

package com.example.waywatch.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted traffic report record.
 *
 * Notes:
 * - Segments are not persisted in v1 to keep schema minimal; they can be added later if required.
 * - The primary key is auto-generated; the domain model uses String ids, so we map Long -> String.
 */
@Entity(tableName = "traffic_reports")
data class TrafficReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // Auto-generated row id
    val routeId: String,                                // Associated route identifier
    val severity: Int,                                  // 0..5 congestion severity
    val source: String,                                 // Enum as string (HISTORICAL/USER)
    val timestampMs: Long                               // Unix time in milliseconds
)

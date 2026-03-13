// Edited: 2026-01-08
// Purpose: Immutable UI state model for the traffic screen; holds reports list, aggregated data, loading flag, and an optional error message.

package com.example.waywatch.ui

import com.example.waywatch.data.TrafficReport
import com.example.waywatch.data.local.entity.AggregatedTrafficEntity

/**
 * Immutable UI model for the traffic screen.
 *
 * Holds a snapshot of the traffic-related data and UI flags that Compose observes.
 * Use with StateFlow in ViewModel and collect in the UI via collectAsState().
 *
 * @property reports current list of traffic reports rendered by the UI
 * @property selectedRouteId currently selected route for viewing aggregated data
 * @property aggregatedData Phase 3 aggregated traffic data (source of truth)
 * @property isLoading indicates when an operation is in progress
 * @property errorMessage optional human-readable error to show in the UI
 */
data class UiState(
    // List of traffic reports emitted from the repository and displayed on the screen
    val reports: List<TrafficReport> = emptyList(),
    // Selected route ID for viewing aggregated traffic
    val selectedRouteId: String = "138", // Default to route 138
    // Aggregated traffic data for the selected route (Phase 3 source of truth)
    val aggregatedData: List<AggregatedTrafficEntity> = emptyList(),
    // True while seeding or submitting updates; allows showing a progress indicator
    val isLoading: Boolean = false,
    // Optional error message to surface to the user when operations fail
    val errorMessage: String? = null,
    // NEW (Phase 3): indicates if a background/foreground sync is in progress
    val isSyncing: Boolean = false,
    // NEW (Phase 3): last successful data refresh time (epoch millis) for UI display
    val lastUpdatedMs: Long? = null
)

data class TrafficReporterUi(
    val routeId: String,
    val severity: Int,
    val segment: List<Pair<Double, Double>>
)

enum class SortMode { NEWEST, OLDEST, HIGHEST_SEVERITY }
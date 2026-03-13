// Edited: 2025-12-27
// Purpose: Data models representing bus routes, coordinates, traffic reports, and user updates for the Ceylon Queue Bus Pulse app.

package com.example.waywatch.data

// Represents a bus route in Sri Lanka with a unique id, display name, and list of stops as LatLng coordinates.
data class BusRoute(
    // Unique identifier for the route (e.g., "route-1")
    val id: String,
    // Human-readable route name (e.g., "Colombo - Kandy")
    val name: String,
    // Ordered list of stop coordinates along the route
    val stops: List<LatLng>
)

// Simple latitude/longitude pair used for mapping and path segments.
data class LatLng(
    // Latitude in decimal degrees (negative for south)
    val lat: Double,
    // Longitude in decimal degrees (negative for west)
    val lng: Double
)

// Origin of a traffic report: historical data (aggregated) or user-submitted.
enum class TrafficSource { HISTORICAL, USER }

// Describes a traffic condition on a specific route segment at a point in time.
data class TrafficReport(
    // Unique id for the report
    val id: String,
    // Route that this report pertains to
    val routeId: String,
    // Severity score (0 = no congestion, 5 = severe congestion)
    val severity: Int, // 0..5
    // The affected path segment represented by a list of LatLng points
    val segment: List<LatLng>,
    // Unix timestamp in milliseconds when this report was generated
    val timestamp: Long,
    // Whether the report came from historical aggregation or user input
    val source: TrafficSource
)

// Captures a user's location update, optionally associated with a route.
data class UserLocationUpdate(
    // Unique id for the user update
    val id: String,
    // Identifier for the user (could be anonymized)
    val userId: String,
    // Latitude of the user's location
    val lat: Double,
    // Longitude of the user's location
    val lng: Double,
    // Unix timestamp in milliseconds when the update was made
    val timestamp: Long,
    // Optional: route the user is currently on or reporting about
    val routeId: String? = null
)

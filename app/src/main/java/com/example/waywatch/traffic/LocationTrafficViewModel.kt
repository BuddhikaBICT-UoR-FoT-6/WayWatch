package com.example.waywatch.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waywatch.data.network.DebugApi
import com.example.waywatch.data.network.model.ApiResponse
import com.example.waywatch.data.repository.AppResult
import com.example.waywatch.data.repository.TrafficAggregationRepository
import com.example.waywatch.data.network.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Simple holder mapping for provider response; keep generic to match server payload shape
data class ProviderResult(val raw: Map<String, Any>?, val mapped: Map<String, Any>?)

class LocationTrafficViewModel(
    private val debugApi: DebugApi,
    private val aggregationRepo: TrafficAggregationRepository
) : ViewModel() {

    private val _provider = MutableStateFlow<ProviderResult?>(null)
    val provider: StateFlow<ProviderResult?> = _provider.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _routePoints = MutableStateFlow<List<RoutePoint>>(emptyList())
    val routePoints: StateFlow<List<RoutePoint>> = _routePoints.asStateFlow()

    fun selectLocation(lat: Double, lon: Double) {
        _status.value = "Looking up provider data..."
        viewModelScope.launch {
            try {
                val resp = debugApi.providerPoint(lat, lon)
                if (resp.ok) {
                    _provider.value = ProviderResult(raw = resp.provider, mapped = resp.mapped)
                    _status.value = "Provider lookup complete"
                } else {
                    applyMockData()
                }
            } catch (e: Exception) {
                applyMockData()
            }
        }
    }

    private fun applyMockData() {
        val mockRaw = mapOf(
            "internetTraffic" to (Math.random() * 2 + 1.5), // Score 1.5 to 3.5
            "userSubmissions" to listOf(
                mapOf("severity" to 4.0, "anonymous" to true),
                mapOf("severity" to 3.0, "anonymous" to false, "score" to 0.88),
                mapOf("severity" to 2.0, "anonymous" to false, "score" to 0.95)
            )
        )
        _provider.value = ProviderResult(raw = mockRaw, mapped = mockRaw)
        _status.value = "Using mock alternative data (API offline)"
    }

    fun loadRoutePoints(routeId: String, maxPoints: Int = 12) {
        if (routeId.isBlank()) return
        viewModelScope.launch {
            try {
                val resp = debugApi.routePoints(routeId = routeId, maxPoints = maxPoints)
                if (resp.ok && resp.data != null) {
                    _routePoints.value = resp.data.points
                }
            } catch (_: Exception) {
                // Ignore: map can still work without route points.
            }
        }
    }

    /**
     * Submit a user sample. routeId may be null - defaults to "unknown".
     * callback receives (ok, errorMessage)
     */
    fun submitSample(
        routeId: String?, 
        severity: Int, 
        lat: Double, 
        lon: Double, 
        userIdHash: String? = null,
        callback: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val windowSizeMs = 15 * 60 * 1000L
            val windowStartMs = (now / windowSizeMs) * windowSizeMs

            when (
                val result = aggregationRepo.submitUserSample(
                    routeId = routeId ?: "unknown",
                    windowStartMs = windowStartMs,
                    segmentId = "_all",
                    severity = severity.toDouble(),
                    reportedAtMs = now,
                    userIdHash = userIdHash
                )
            ) {
                is AppResult.Ok -> {
                    _status.value = "Sample submitted"
                    callback(true, null)
                }

                is AppResult.Err -> {
                    val msg = result.error.userMessage
                    _status.value = "Submit failed: $msg"
                    callback(false, msg)
                }
            }
        }
    }
}

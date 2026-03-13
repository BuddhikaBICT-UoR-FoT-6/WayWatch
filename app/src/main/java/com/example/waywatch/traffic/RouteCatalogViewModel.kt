package com.example.waywatch.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waywatch.data.network.OsmRoutesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RouteChip(
    val ref: String,
    val name: String? = null
)

class RouteCatalogViewModel(
    private val osmApi: OsmRoutesApi
) : ViewModel() {

    private val _routes = MutableStateFlow<List<RouteChip>>(emptyList())
    val routes: StateFlow<List<RouteChip>> = _routes

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun loadNearby(lat: Double, lon: Double, radiusKm: Double = 6.0) {
        viewModelScope.launch {
            try {
                _status.value = null
                val resp = osmApi.nearby(lat = lat, lon = lon, radiusKm = radiusKm, limit = 10)
                if (resp.ok && resp.data != null) {
                    _routes.value = resp.data
                        .filter { it.ref.isNotBlank() }
                        .map { RouteChip(ref = it.ref.trim(), name = it.name) }
                        .distinctBy { it.ref }
                        .take(10)
                } else {
                    _status.value = resp.error ?: "Failed to load nearby routes"
                    _routes.value = emptyList()
                }
            } catch (t: Throwable) {
                _status.value = t.message ?: "Failed to load nearby routes"
                _routes.value = emptyList()
            }
        }
    }
}


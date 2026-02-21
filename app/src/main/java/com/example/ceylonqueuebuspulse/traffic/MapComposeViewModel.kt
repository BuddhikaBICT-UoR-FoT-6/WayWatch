package com.example.ceylonqueuebuspulse.traffic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchApi
import com.example.ceylonqueuebuspulse.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException

data class PlaceResult(val label: String, val lat: Double, val lon: Double)

class MapComposeViewModel(
    private val tomTomSearchApi: TomTomSearchApi,
    private val locVm: LocationTrafficViewModel

) : ViewModel(){

    private val _places = MutableStateFlow<List<PlaceResult>>(emptyList())
    val places: StateFlow<List<PlaceResult>> = _places

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /**
     * Search with retry + basic backoff. Updates [places] on success and [status] on progress/errors.
     */
    fun search(query: String, apiKey: String, maxAttempts: Int = 3) {
        if (query.isBlank()) {
            _status.value = "Query is blank"
            return
        }
        if (apiKey.isBlank()) {
            _status.value = "TomTom API key is missing. Set TOMTOM_API_KEY in local.properties/gradle.properties and rebuild."
            return
        }

        viewModelScope.launch {
            _status.value = "Searching..."
            var attempt = 0
            var lastError: Throwable? = null
            var lastErrorMessage: String? = null
            while (attempt < maxAttempts) {

                try {
                    val res = tomTomSearchApi.search(query.trim(), apiKey, limit = 10)
                    val converted = res.results.mapNotNull { r ->
                        val pos = r.position
                        val lat = pos?.lat
                        val lon = pos?.lon
                        if (lat != null && lon != null) {
                            val label = r.address?.freeformAddress ?: "${lat}, ${lon}"
                            PlaceResult(label = label, lat = lat, lon = lon)
                        } else null
                    }
                    _places.value = converted
                    _status.value = if (converted.isEmpty()) "No results" else null
                    return@launch
                } catch (t: Throwable) {
                    lastError = t
                    val msg = when (t) {
                        is HttpException -> {
                            if (t.code() == 403) {
                                "TomTom Search returned 403 (Forbidden). Check that TOMTOM_API_KEY is correct and that Search API is enabled for the project in TomTom Dashboard."
                            } else {
                                "HTTP ${t.code()} ${t.message()}"
                            }
                        }
                        else -> t.message ?: "Unknown error"
                    }
                    lastErrorMessage = msg
                    AppLogger.w("MapComposeViewModel", "Search attempt ${attempt + 1} failed: $msg", t)

                    attempt++
                    if (attempt < maxAttempts) {
                        delay(400L * attempt)
                    }
                }
            }
            _status.value = "Search failed: ${lastErrorMessage ?: lastError?.message ?: "Unknown error"}"
        }
    }

    fun selectPlace(place: PlaceResult) {
        // Move the unified pipeline: call provider lookup and update UI via LocationTrafficViewModel
        locVm.selectLocation(place.lat, place.lon)
    }

    fun submitSampleForPlace(place: PlaceResult, severity: Int) {
        locVm.submitSample(null, severity, place.lat, place.lon) { ok, err ->
            _status.value = if (ok) "Sample submitted" else "Submit failed: $err"
        }
    }
}

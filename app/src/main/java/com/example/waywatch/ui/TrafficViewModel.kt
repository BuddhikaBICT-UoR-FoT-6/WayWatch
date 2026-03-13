package com.example.waywatch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waywatch.data.UserLocationUpdate
import com.example.waywatch.data.location.LocationTracker
import com.example.waywatch.data.local.entity.AggregatedTrafficEntity
import com.example.waywatch.data.repository.AppResult
import com.example.waywatch.data.repository.TrafficAggregationRepository
import com.example.waywatch.data.repository.TrafficRepository
import com.example.waywatch.util.NetworkException
import com.example.waywatch.util.RetryUtil
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficViewModel(
    private val repository: TrafficRepository,
    private val aggregationRepository: TrafficAggregationRepository,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _lastUpdatedMs = MutableStateFlow<Long?>(null)
    private val _selectedRouteId = MutableStateFlow("138")

    init {
        // Collect location in the background; submit each update silently.
        locationTracker.observeLocation()
            .onEach { location ->
                val routeId = _selectedRouteId.value
                if (routeId.isNotBlank()) {
                    submitUserLocation(location.latitude, location.longitude, routeId)
                }
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<UiState> = combine(
        repository.reports,
        _selectedRouteId.flatMapLatest { aggregationRepository.observeAggregatedTraffic(it) },
        _selectedRouteId,
        _isLoading,
        _errorMessage,
        _isSyncing,
        _lastUpdatedMs
    ) { reports, aggregates, routeId, isLoading, errorMsg, isSyncing, lastUpdated ->
        @Suppress("UNCHECKED_CAST")
        UiState(
            reports = reports as List<com.example.waywatch.data.TrafficReport>,
            aggregatedData = aggregates as List<AggregatedTrafficEntity>,
            selectedRouteId = routeId as String,
            isLoading = isLoading as Boolean,
            errorMessage = errorMsg as String?,
            isSyncing = isSyncing as Boolean,
            lastUpdatedMs = lastUpdated as Long?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun selectRoute(routeId: String) {
        _selectedRouteId.value = routeId
    }

    fun submitUserLocation(lat: Double, lng: Double, routeId: String) {
        viewModelScope.launch {
            if (routeId.isBlank()) {
                _errorMessage.value = "Route ID is required"
                return@launch
            }
            _isLoading.value = true
            _errorMessage.value = null
            val update = UserLocationUpdate(
                id = System.currentTimeMillis().toString(),
                userId = "anonymous",
                lat = lat,
                lng = lng,
                timestamp = System.currentTimeMillis(),
                routeId = routeId
            )
            when (val result = repository.submitUserUpdate(update)) {
                is AppResult.Ok -> _isLoading.value = false
                is AppResult.Err -> {
                    _isLoading.value = false
                    _errorMessage.value = result.error.userMessage
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            try {
                val routeId = _selectedRouteId.value
                when (val result = aggregationRepository.getAggregates(routeId)) {
                    is AppResult.Ok -> {
                        _isSyncing.value = false
                        _lastUpdatedMs.value = System.currentTimeMillis()
                    }
                    is AppResult.Err -> {
                        _isSyncing.value = false
                        _errorMessage.value = result.error.userMessage
                    }
                }
            } catch (t: Throwable) {
                val msg = when (val mapped = RetryUtil.mapException(t)) {
                    is NetworkException -> mapped.message ?: "Failed to sync data"
                }
                _isSyncing.value = false
                _errorMessage.value = msg
            }
        }
    }

    fun seedHistoricalData(sample: List<com.example.waywatch.data.TrafficReport>) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            when (val result = repository.seedHistoricalData(sample)) {
                is AppResult.Ok -> _isLoading.value = false
                is AppResult.Err -> {
                    _isLoading.value = false
                    _errorMessage.value = result.error.userMessage
                }
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

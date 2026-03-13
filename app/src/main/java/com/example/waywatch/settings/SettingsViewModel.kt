package com.example.waywatch.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setSeverityThreshold(threshold: Double) {
        viewModelScope.launch { repo.setSeverityThreshold(threshold) }
    }

    fun setRefreshIntervalMinutes(minutes: Int) {
        viewModelScope.launch { repo.setRefreshIntervalMinutes(minutes) }
    }

    fun setPreferredRoutes(routes: Set<String>) {
        viewModelScope.launch { repo.setPreferredRoutes(routes) }
    }

    fun setWatchedRoutes(routes: Set<String>) {
        viewModelScope.launch { repo.setWatchedRoutes(routes) }
    }
}

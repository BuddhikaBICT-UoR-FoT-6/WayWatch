package com.example.waywatch.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.waywatch.data.network.model.MongoApi
import com.example.waywatch.data.network.model.SessionDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val isLoading: Boolean = false,
    val sessions: List<SessionDto> = emptyList(),
    val error: String? = null
)

class AccountViewModel(private val api: MongoApi) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    fun fetchSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getSessions()
                if (response.ok) {
                    _uiState.update { it.copy(isLoading = false, sessions = response.data ?: emptyList()) }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Failed to fetch sessions") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun revokeSession(deviceId: String) {
        viewModelScope.launch {
            try {
                val response = api.revokeSession(deviceId)
                if (response.ok) {
                    // Refresh the list
                    fetchSessions()
                } else {
                    _uiState.update { it.copy(error = response.message ?: "Failed to revoke session") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}

package com.example.ceylonqueuebuspulse.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ceylonqueuebuspulse.analytics.FirebaseAnalyticsLogger
import com.example.ceylonqueuebuspulse.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val analytics = FirebaseAnalyticsLogger()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    init {
        viewModelScope.launch {
            authRepository.accessToken.collectLatest { token ->
                _uiState.value = _uiState.value.copy(isLoggedIn = !token.isNullOrBlank())
            }
        }
    }

    fun setEmail(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = null, successMessage = null)
    }

    fun setPassword(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = null, successMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            errorMessage = null,
            successMessage = null
        )
    }

    private fun isEmailValid(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isPasswordValid(password: String, isRegisterMode: Boolean): Boolean {
        return if (isRegisterMode) password.length >= 8 else password.isNotEmpty()
    }

    fun submit() {
        val email = _uiState.value.email.trim()
        val password = _uiState.value.password
        val isRegister = _uiState.value.isRegisterMode
        if (!isEmailValid(email)) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter a valid email")
            return
        }
        if (!isPasswordValid(password, isRegister)) {
            _uiState.value = _uiState.value.copy(errorMessage = if (isRegister) "Password must be at least 8 characters" else "Password is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, successMessage = null)

            val result = if (_uiState.value.isRegisterMode) {
                authRepository.register(email, password)
            } else {
                authRepository.login(email, password)
            }

            result.fold(
                onSuccess = {
                    val mode = if (_uiState.value.isRegisterMode) "register" else "login"
                    analytics.logEvent("auth_success", mapOf("mode" to mode))

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = if (_uiState.value.isRegisterMode) "Account created" else "Welcome back"
                    )
                },
                onFailure = { t ->
                    val mode = if (_uiState.value.isRegisterMode) "register" else "login"
                    analytics.logEvent(
                        "auth_failure",
                        mapOf(
                            "mode" to mode,
                            "message" to (t.message ?: "Authentication failed")
                        )
                    )

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Authentication failed"
                    )
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            analytics.logEvent("logout")
            _uiState.value = _uiState.value.copy(successMessage = "Logged out")
        }
    }

    fun consumeMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

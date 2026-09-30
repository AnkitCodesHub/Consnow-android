package com.Consnow.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.Result
import com.Consnow.features.auth.domain.AuthRepository
import com.Consnow.features.auth.domain.model.AuthState
import com.Consnow.features.auth.domain.usecase.LoginUseCase
import com.Consnow.features.auth.domain.usecase.RegisterUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()

    init { checkSession() }

    private fun checkSession() {
        viewModelScope.launch {
            _state.update { it.copy(isLoggedIn = authRepository.isLoggedIn()) }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = registerUseCase(name, email, password)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = loginUseCase(email, password)) {
                is Result.Success -> _state.update { it.copy(isLoading = false, isLoggedIn = true) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is Result.Loading -> {}
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { AuthState(isLoggedIn = false) }
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    class Factory(
        private val loginUseCase: LoginUseCase,
        private val registerUseCase: RegisterUseCase,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AuthViewModel(loginUseCase, registerUseCase, authRepository) as T
    }
}

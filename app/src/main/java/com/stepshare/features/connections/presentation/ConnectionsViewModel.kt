package com.Consnow.features.connections.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.Result
import com.Consnow.core.datastore.TokenManager
import com.Consnow.features.connections.domain.model.ConnectionsState
import com.Consnow.features.connections.domain.usecase.ConnectUseCase
import com.Consnow.features.connections.domain.usecase.GetConnectionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectionsViewModel(
    private val connectUseCase: ConnectUseCase,
    private val getConnectionsUseCase: GetConnectionsUseCase,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsState())
    val state = _state.asStateFlow()

    init {
        loadConnectionCode()
        loadConnections()
    }

    private fun loadConnectionCode() {
        viewModelScope.launch {
            tokenManager.connectionCodeFlow.collect { code ->
                _state.update { it.copy(myConnectionCode = code ?: "") }
            }
        }
    }

    fun loadConnections() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = getConnectionsUseCase()) {
                is Result.Success -> _state.update { it.copy(isLoading = false, connections = result.data) }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                else -> {}
            }
        }
    }

    fun connect(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, connectSuccess = false) }
            when (val result = connectUseCase(code)) {
                is Result.Success -> {
                    _state.update { it.copy(isLoading = false, connectSuccess = true) }
                    loadConnections()
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                else -> {}
            }
        }
    }

    class Factory(
        private val connectUseCase: ConnectUseCase,
        private val getConnectionsUseCase: GetConnectionsUseCase,
        private val tokenManager: TokenManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionsViewModel(connectUseCase, getConnectionsUseCase, tokenManager) as T
    }
}

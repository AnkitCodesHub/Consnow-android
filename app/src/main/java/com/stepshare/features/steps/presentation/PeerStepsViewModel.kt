package com.Consnow.features.steps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.Result
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PeerStepsViewModel(
    private val getPeerStepsUseCase: GetPeerStepsUseCase,
    private val peerId: String
) : ViewModel() {

    private val _state = MutableStateFlow(PeerStepsState(isLoading = true))
    val state = _state.asStateFlow()

    init { loadPeerSteps() }

    fun loadPeerSteps() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = getPeerStepsUseCase(peerId)) {
                is Result.Success -> _state.update {
                    it.copy(isLoading = false, peerName = result.data.userName, stepCount = result.data.stepCount)
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                else -> {}
            }
        }
    }

    fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                loadPeerSteps()
            }
        }
    }

    class Factory(
        private val getPeerStepsUseCase: GetPeerStepsUseCase,
        private val peerId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PeerStepsViewModel(getPeerStepsUseCase, peerId) as T
    }
}

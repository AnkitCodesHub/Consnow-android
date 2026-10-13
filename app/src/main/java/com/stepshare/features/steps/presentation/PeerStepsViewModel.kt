package com.Consnow.features.steps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import android.util.Log

class PeerStepsViewModel(
    private val getPeerStepsUseCase: GetPeerStepsUseCase,
    private val apiService: ApiService,
    private val peerId: String,
) : ViewModel() {

    // ── lock status ──────────────────────────────────────────────────────────
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _lastUnlockedAtMs = MutableStateFlow(0L)
    val lastUnlockedAtMs: StateFlow<Long> = _lastUnlockedAtMs.asStateFlow()

    // ── legacy text label (kept for backward compat) ─────────────────────────
    private val _peerLastUnlocked = MutableStateFlow("—")
    val peerLastUnlocked: StateFlow<String> = _peerLastUnlocked.asStateFlow()

    // ── steps state ──────────────────────────────────────────────────────────
    private val _state = MutableStateFlow(PeerStepsState(isLoading = true))
    val state = _state.asStateFlow()

    private var isInitialLoad = true
    private var refreshStarted = false

    init {
        loadPeerSteps()

        // Initial fetch from REST — replaces old getPeerLastUnlock call
        viewModelScope.launch {
            try {
                val response = apiService.getPeerActivity(peerId)
                Log.d("PeerLock", "response code: ${response.code()}")
                Log.d("PeerLock", "body: ${response.body()}")
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        Log.d("PeerLock", "isLocked=${body.isLocked} unlockedAt=${body.unlockedAt}")
                        _isLocked.value = body.isLocked
                        body.unlockedAt?.let { iso ->
                            val ms = Instant.parse(iso).toEpochMilli()
                            _lastUnlockedAtMs.value = ms
                            _peerLastUnlocked.value = formatUnlockTime(ms)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PeerLock", "failed: ${e.message}")
            }
        }

        // Real-time unlock event via WebSocket
        viewModelScope.launch {
            ActivityEventBus.peerUnlocked
                .filter { it.userId == peerId }
                .collect { event ->
                    _isLocked.value = false
                    _lastUnlockedAtMs.value = event.unlockedAtMs
                    _peerLastUnlocked.value = formatUnlockTime(event.unlockedAtMs)
                }
        }

        // Real-time lock event via WebSocket
        viewModelScope.launch {
            ActivityEventBus.peerLocked
                .filter { it.userId == peerId }
                .collect { _isLocked.value = true }
        }
        // Polling fallback — updates lock status every 15s when socket isn't connected
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                try {
                    val response = apiService.getPeerActivity(peerId)
                    if (response.isSuccessful) {
                        response.body()?.let { body ->
                            _isLocked.value = body.isLocked
                            body.unlockedAt?.let { iso ->
                                val ms = Instant.parse(iso).toEpochMilli()
                                _lastUnlockedAtMs.value = ms
                                _peerLastUnlocked.value = formatUnlockTime(ms)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun loadPeerSteps() {
        viewModelScope.launch {
            if (isInitialLoad) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            when (val result = getPeerStepsUseCase(peerId)) {
                is Result.Success -> {
                    isInitialLoad = false
                    _state.update {
                        it.copy(
                            isLoading = false,
                            peerName = result.data.userName,
                            stepCount = result.data.stepCount,
                            isActive = result.data.isActive,
                            lastActiveAt = result.data.lastActiveAt,
                            error = null,
                        )
                    }
                }
                is Result.Error -> {
                    if (isInitialLoad) {
                        _state.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
                else -> {}
            }
        }
    }

    fun startAutoRefresh() {
        if (refreshStarted) return
        refreshStarted = true
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                loadPeerSteps()
            }
        }
    }

    private fun formatUnlockTime(ms: Long): String {
        val seconds = (System.currentTimeMillis() - ms) / 1000
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    }

    class Factory(
        private val getPeerStepsUseCase: GetPeerStepsUseCase,
        private val apiService: ApiService,
        private val peerId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PeerStepsViewModel(getPeerStepsUseCase, apiService, peerId) as T
    }
}
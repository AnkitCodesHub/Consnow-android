package com.Consnow.features.steps.presentation

import android.util.Log
import com.Consnow.BuildConfig
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

class PeerStepsViewModel(
    private val getPeerStepsUseCase: GetPeerStepsUseCase,
    private val apiService: ApiService,
    private val peerId: String,
) : ViewModel() {

    // Whether the peer's phone is locked.
    // Defaults to true — treat unknown/offline state as locked.
    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Whether the peer is online (heartbeat received in the last 90s).
    // Defaults to false — treat unknown state as offline.
    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Epoch millis of peer's last unlock — fed into LockStatusWidget timer.
    // Only ever moves forward via applyTimestamp() to prevent stale overwrites.
    private val _lastUnlockedAtMs = MutableStateFlow(0L)
    val lastUnlockedAtMs: StateFlow<Long> = _lastUnlockedAtMs.asStateFlow()

    // Legacy text label — kept for backward compat with any screen still reading it
    private val _peerLastUnlocked = MutableStateFlow("—")
    val peerLastUnlocked: StateFlow<String> = _peerLastUnlocked.asStateFlow()

    // Step count + activity state for PeerStepsScreen
    private val _state = MutableStateFlow(PeerStepsState(isLoading = true))
    val state = _state.asStateFlow()

    private var isInitialLoad = true
    private var refreshStarted = false

    init {
        loadPeerSteps()
        fetchPeerActivity()
        listenForWebSocketEvents()
        startActivityPolling()
    }

    /**
     * Initial REST fetch of peer's lock state.
     * Runs once on ViewModel creation to populate the widget before
     * WebSocket or polling kicks in.
     * Uses applyTimestamp() so a late-arriving WebSocket event cannot be
     * overwritten by a slow REST response.
     * On failure, defaults to locked.
     */
    private fun fetchPeerActivity() {
        viewModelScope.launch {
            try {
                val response = apiService.getPeerActivity(peerId)
                if (BuildConfig.DEBUG) Log.d("PeerLock", "code=${response.code()} body=${response.body()}")
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        if (BuildConfig.DEBUG) Log.d("PeerLock", "isLocked=${body.isLocked} unlockedAt=${body.unlockedAt}")
                        _isLocked.value = if (!body.isOnline) true else body.isLocked
                        _isOnline.value = body.isOnline
                        body.unlockedAt?.let { iso ->
                            applyTimestamp(Instant.parse(iso).toEpochMilli())
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PeerLock", "fetchPeerActivity failed: ${e.message}")
                _isLocked.value = true  // offline — show as locked
                _isOnline.value = false
            }
        }
    }

    /**
     * Listens for real-time lock/unlock events from WebSocket via ActivityEventBus.
     * Filters by peerId so we only react to events for this specific peer.
     * When WebSocket is not connected, the 15s polling fallback covers this.
     *
     * peerUnlocked: server sends the exact unlock timestamp — passed to
     *   applyTimestamp() so the "X ago" ticker starts from the real moment.
     *
     * peerLocked: server may send lockedAt (the lock transition timestamp).
     *   If present, applyTimestamp() advances the reference time so the widget
     *   shows "locked X ago" rather than "unlocked X ago".
     *   If absent (older server), the last known unlockedAt is kept as-is.
     */
    private fun listenForWebSocketEvents() {
        // Peer unlocked their phone
        viewModelScope.launch {
            ActivityEventBus.peerUnlocked
                .filter { it.userId == peerId }
                .collect { event ->
                    _isLocked.value = false
                    applyTimestamp(event.unlockedAtMs)
                }
        }

        // Peer locked their phone
        viewModelScope.launch {
            ActivityEventBus.peerLocked
                .filter { it.userId == peerId }
                .collect { event ->
                    _isLocked.value = true
                    // Advance reference time to the lock moment if server provided it,
                    // so the widget counts from when the phone locked, not when it unlocked
                    event.lockedAtMs?.let(::applyTimestamp)
                }
        }
    }

    /**
     * Polls GET /activity/peer/:id every 15s as fallback when WebSocket is not connected.
     * Uses applyTimestamp() so a stale poll cannot overwrite a fresher WebSocket event.
     * On network failure, sets isLocked = true so widget shows locked instead of stale state.
     */
    private fun startActivityPolling() {
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                try {
                    val response = apiService.getPeerActivity(peerId)
                    if (response.isSuccessful) {
                        response.body()?.let { body ->
                            _isLocked.value = if (!body.isOnline) true else body.isLocked
                            _isOnline.value = body.isOnline
                            body.unlockedAt?.let { iso ->
                                applyTimestamp(Instant.parse(iso).toEpochMilli())
                            }
                        }
                    }
                } catch (_: Exception) {
                    _isLocked.value = true  // offline — show as locked
                    _isOnline.value = false
                }
            }
        }
    }

    /**
     * Fetches peer's step count and activity status.
     * Called once on init and then every 5s via startAutoRefresh().
     */
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
                    // Keep showing last known data on error — don't replace with error screen
                    if (isInitialLoad) {
                        _state.update { it.copy(isLoading = false, error = result.message) }
                    }
                }
                else -> {}
            }
        }
    }

    /**
     * Starts auto-refreshing peer steps every 5s.
     * Called from PeerStepsScreen when it becomes visible.
     * Guard flag prevents duplicate loops if called multiple times.
     */
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

    /**
     * Advances the unlock/lock reference timestamp only if the new value is
     * more recent than what we already have.
     *
     * This guards against three stale-write scenarios:
     *   1. A slow REST poll arriving after a faster WebSocket event
     *   2. Out-of-order WebSocket events (rare but possible on reconnect)
     *   3. A 15s poll overwriting a lock event that just arrived via WebSocket
     *
     * Both the "unlocked X ago" and "locked X ago" cases flow through here —
     * the caller decides what the timestamp means, this just ensures time
     * never goes backwards.
     */
    private fun applyTimestamp(ms: Long) {
        if (ms >= _lastUnlockedAtMs.value) {
            _lastUnlockedAtMs.value = ms
            _peerLastUnlocked.value = formatUnlockTime(ms)
        }
    }

    /**
     * Formats epoch millis into a human-readable "X ago" string.
     * Used for the legacy _peerLastUnlocked label.
     */
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
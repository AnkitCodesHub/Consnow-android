package com.Consnow.features.steps.presentation

import android.util.Log
import com.Consnow.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.common.OFFLINE_MESSAGE
import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.features.steps.data.cache.PeerProfileCache
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.coroutineScope
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

    // Last known profile for this friend (from Home polling, prefetch or a previous visit).
    // Seeds every flow below so the screen draws instantly; the network refresh then
    // replaces it silently. Must be declared before the flows that read it.
    private val cached = PeerProfileCache.get(peerId)
    private val cachedActivity = cached?.takeIf { it.hasActivity }

    // Whether the peer's phone is locked.
    // Defaults to true — treat unknown/offline state as locked.
    private val _isLocked = MutableStateFlow(cachedActivity?.isLocked ?: true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    // Whether the peer is online (heartbeat received in the last 90s).
    // Defaults to false — treat unknown state as offline.
    private val _isOnline = MutableStateFlow(cachedActivity?.isOnline ?: false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Epoch millis of peer's last unlock — fed into LockStatusWidget timer.
    // Only ever moves forward via applyTimestamp() to prevent stale overwrites.
    private val _lastUnlockedAtMs = MutableStateFlow(cached?.lastUnlockedAtMs ?: 0L)
    val lastUnlockedAtMs: StateFlow<Long> = _lastUnlockedAtMs.asStateFlow()

    // Legacy text label — kept for backward compat with any screen still reading it
    private val _peerLastUnlocked = MutableStateFlow(
        cached?.lastUnlockedAtMs?.takeIf { it > 0 }?.let(::formatUnlockTime) ?: "—"
    )
    val peerLastUnlocked: StateFlow<String> = _peerLastUnlocked.asStateFlow()

    // Step count + activity state for PeerStepsScreen
    // With cached steps: show them right away (no spinner). Without: spinner as before.
    private val _state = MutableStateFlow(
        cached?.let { c ->
            c.todaySteps()?.let { steps ->
                PeerStepsState(
                    isLoading = false,
                    peerName = c.name,
                    stepCount = steps,
                    isActive = c.isActive,
                    lastActiveAt = c.lastActiveAt,
                )
            }
        } ?: PeerStepsState(isLoading = true, peerName = cached?.name.orEmpty())
    )
    val state = _state.asStateFlow()

    // When cached data is on screen, a failed first fetch keeps it instead of showing an error
    private var isInitialLoad = _state.value.isLoading
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
        viewModelScope.launch { refreshActivity() }
    }

    /** One REST fetch of lock/online status. Used on open and by pull-to-refresh. */
    private suspend fun refreshActivity() {
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
                    saveActivityToCache()
                }
            }
        } catch (e: Exception) {
            Log.e("PeerLock", "fetchPeerActivity failed: ${e.message}")
            _isLocked.value = true  // offline — show as locked
            _isOnline.value = false
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
                    saveActivityToCache()
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
                    saveActivityToCache()
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
                            saveActivityToCache()
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
        viewModelScope.launch { fetchSteps() }
    }

    /**
     * Pull-to-refresh: reloads steps and lock status together and returns only when
     * both are done, so the screen knows when to hide the refresh spinner.
     */
    suspend fun refresh() {
        coroutineScope {
            launch { fetchSteps() }
            launch { refreshActivity() }
        }
    }

    private suspend fun fetchSteps() {
        if (isInitialLoad) {
            _state.update { it.copy(isLoading = true, error = null) }
        }
        when (val result = getPeerStepsUseCase(peerId)) {
            is Result.Success -> {
                isInitialLoad = false
                PeerProfileCache.putSteps(
                    peerId = peerId,
                    name = result.data.userName,
                    stepCount = result.data.stepCount,
                    date = result.data.date,
                    isActive = result.data.isActive,
                    lastActiveAt = result.data.lastActiveAt,
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        peerName = result.data.userName,
                        stepCount = result.data.stepCount,
                        isActive = result.data.isActive,
                        lastActiveAt = result.data.lastActiveAt,
                        error = null,
                        isOffline = false,
                    )
                }
            }
            is Result.Error -> {
                // Keep showing last known data on error — don't replace with error screen
                val offline = result.message == OFFLINE_MESSAGE
                if (isInitialLoad) {
                    _state.update { it.copy(isLoading = false, error = result.message, isOffline = offline) }
                } else {
                    _state.update { it.copy(isOffline = offline) }
                }
            }
            else -> {}
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

    /** Saves the current lock/online state so the next visit opens instantly. Successes only. */
    private fun saveActivityToCache() {
        PeerProfileCache.putActivity(peerId, _isOnline.value, _isLocked.value, _lastUnlockedAtMs.value)
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
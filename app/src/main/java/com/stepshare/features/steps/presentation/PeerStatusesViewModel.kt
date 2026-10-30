package com.Consnow.features.steps.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.network.api.ApiService
import com.Consnow.features.steps.data.cache.PeerProfileCache
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** Live summary of one friend, shown on the Home screen friends list. */
data class PeerLiveStatus(
    val stepCount: Int? = null,      // null until first loaded
    val isOnline: Boolean = false,
    val isLocked: Boolean = true,
    val lastUnlockedAtMs: Long = 0L,
)

/**
 * Loads online status for every connected friend so the Home
 * screen can show them in its list. Uses the same endpoints and rules as
 * PeerStepsViewModel (offline => locked; unlock time only moves forward).
 * The peer screen itself is unchanged and keeps its own ViewModel.
 */
class PeerStatusesViewModel(
    private val getPeerStepsUseCase: GetPeerStepsUseCase,
    private val apiService: ApiService,
) : ViewModel() {

    private val _statuses = MutableStateFlow<Map<String, PeerLiveStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, PeerLiveStatus>> = _statuses.asStateFlow()

    private var trackedIds: List<String> = emptyList()
    private var pollJob: Job? = null

    /** Call whenever the connections list changes. Restarts polling for the new ids. */
    fun track(peerIds: List<String>) {
        if (peerIds == trackedIds && pollJob?.isActive == true) return
        trackedIds = peerIds
        pollJob?.cancel()
        if (peerIds.isEmpty()) return
        pollJob = viewModelScope.launch {
            while (true) {
                refreshAll(peerIds)
                delay(POLL_MS)
            }
        }
    }

    private suspend fun refreshAll(peerIds: List<String>) = coroutineScope {
        peerIds.map { id -> async { refreshOne(id) } }.awaitAll()
    }

    private suspend fun refreshOne(peerId: String) {
        // Lock + online status
        var isOnline = false
        var isLocked = true
        var unlockedAtMs: Long? = null
        try {
            val response = apiService.getPeerActivity(peerId)
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    isOnline = body.isOnline
                    isLocked = if (!body.isOnline) true else body.isLocked
                    unlockedAtMs = body.unlockedAt?.let {
                        runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
                    }
                    // Warm the friend profile cache so tapping this friend opens instantly
                    PeerProfileCache.putActivity(peerId, isOnline, isLocked, unlockedAtMs)
                }
            }
        } catch (e: Exception) {
            Log.w("PeerStatuses", "activity fetch failed for $peerId: ${e.message}")
        }

        _statuses.update { current ->
            val old = current[peerId] ?: PeerLiveStatus()
            current + (peerId to old.copy(
                isOnline = isOnline,
                isLocked = isLocked,
                lastUnlockedAtMs = maxOf(old.lastUnlockedAtMs, unlockedAtMs ?: 0L),
            ))
        }
    }

    class Factory(
        private val getPeerStepsUseCase: GetPeerStepsUseCase,
        private val apiService: ApiService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PeerStatusesViewModel(getPeerStepsUseCase, apiService) as T
    }

    private companion object {
        const val POLL_MS = 20_000L
    }
}

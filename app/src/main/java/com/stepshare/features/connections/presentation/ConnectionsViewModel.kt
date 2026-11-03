package com.Consnow.features.connections.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.common.ConnectionEventBus
import com.Consnow.core.common.Result
import com.Consnow.core.datastore.TokenManager
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.ConnectionsState
import com.Consnow.features.connections.domain.model.PeerConnectionStatus
import com.Consnow.features.connections.domain.model.PeerInfo
import com.Consnow.features.connections.domain.model.PendingUndo
import com.Consnow.features.connections.domain.usecase.ConnectUseCase
import com.Consnow.features.connections.domain.usecase.GetConnectionsUseCase
import com.Consnow.features.steps.data.cache.PeerProfileCache
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Friends list + connect / disconnect.
 *
 * Every button flips the row immediately (optimistic), then calls the server. If the
 * server says no, the row goes back and a short message explains why. While a call for
 * a friend is in flight, list refreshes don't overwrite that friend's row.
 */
class ConnectionsViewModel(
    private val connectUseCase: ConnectUseCase,
    private val getConnectionsUseCase: GetConnectionsUseCase,
    private val tokenManager: TokenManager,
    private val repository: ConnectionsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsState())
    val state = _state.asStateFlow()

    /** Friends with a button tap still being sent to the server. */
    private val busy = mutableSetOf<String>()
    private var undoJob: Job? = null
    /** In-flight disconnect calls, so Undo waits for the disconnect to land first. */
    private val disconnectJobs = mutableMapOf<String, Job>()

    init {
        loadConnectionCode()
        loadConnections()
        loadRequests()
        refreshPeriodically()
        listenForConnectionChanges()
        registerPushToken()
    }

    // The list used to load once. If the app opened while offline (e.g. in the morning
    // before Wi-Fi/data came back), it stayed empty/errored until the app was reinstalled.
    private fun refreshPeriodically() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                loadConnections()
                loadRequests()
            }
        }
    }

    /** Live: the other person disconnected / accepted / asked → reload within a second. */
    private fun listenForConnectionChanges() {
        viewModelScope.launch {
            ConnectionEventBus.events.collect { event ->
                if (event.type == "disconnected") {
                    PeerProfileCache.remove(event.peerId)
                    // Hide their data right away, before the reload comes back
                    setStatus(event.peerId, PeerConnectionStatus.DISCONNECTED)
                }
                loadConnections()
                loadRequests()
            }
        }
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
            // Only show the spinner when there is nothing to show yet (no flicker on refresh)
            _state.update { it.copy(isLoading = it.connections.isEmpty()) }
            when (val result = getConnectionsUseCase()) {
                is Result.Success -> _state.update { s ->
                    // Keep our optimistic row for friends whose tap is still being sent
                    val merged = result.data.map { fresh ->
                        if (fresh.id in busy) s.connections.firstOrNull { it.id == fresh.id } ?: fresh else fresh
                    }
                    s.copy(isLoading = false, connections = merged, error = null)
                }
                // Offline: keep the last known list instead of replacing it with an error
                is Result.Error -> _state.update { it.copy(isLoading = false, error = if (it.connections.isEmpty()) result.message else null) }
                else -> {}
            }
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            val result = repository.getIncomingRequests()
            if (result is Result.Success) _state.update { it.copy(requests = result.data) }
        }
    }

    fun connect(code: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, connectSuccess = false) }
            when (val result = connectUseCase(code)) {
                is Result.Success -> {
                    val peer = result.data
                    val text = when (peer.status) {
                        PeerConnectionStatus.CONNECTED -> "Connected!"
                        PeerConnectionStatus.COOLDOWN -> "${peer.name} declined recently. Try again later."
                        else -> "Request sent to ${peer.name}"
                    }
                    _state.update { it.copy(isLoading = false, connectSuccess = true, connectSuccessText = text) }
                    loadConnections()
                }
                is Result.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                else -> {}
            }
        }
    }

    // ── Disconnect + Undo ─────────────────────────────────────────────────

    fun disconnect(peer: PeerInfo) {
        PeerProfileCache.remove(peer.id) // their data disappears from this phone immediately
        setStatus(peer.id, PeerConnectionStatus.DISCONNECTED)
        showUndo(peer)
        disconnectJobs[peer.id] = runAction(peer.id, revertTo = peer, failMessage = "Couldn't disconnect from ${firstName(peer)}") {
            repository.disconnect(peer.id)
        }
    }

    fun undoDisconnect() {
        val undo = _state.value.pendingUndo ?: return
        undoJob?.cancel()
        _state.update { it.copy(pendingUndo = null) }
        val peer = undo.peer
        setStatus(peer.id, PeerConnectionStatus.CONNECTED)
        viewModelScope.launch {
            busy += peer.id
            // The disconnect may still be on its way; undoing before it lands would be undone by it
            disconnectJobs.remove(peer.id)?.join()
            val result = repository.undoDisconnect(peer.id)
            busy -= peer.id
            if (result is Result.Error) {
                setStatus(peer.id, PeerConnectionStatus.DISCONNECTED)
                showMessage("Couldn't undo. Tap Connect to send a request instead.")
            }
            loadConnections()
        }
    }

    private fun showUndo(peer: PeerInfo) {
        undoJob?.cancel()
        _state.update { it.copy(pendingUndo = PendingUndo(peer, System.currentTimeMillis())) }
        undoJob = viewModelScope.launch {
            delay(UNDO_SHOWN_MS)
            _state.update { if (it.pendingUndo?.peer?.id == peer.id) it.copy(pendingUndo = null) else it }
        }
    }

    fun dismissUndo() {
        undoJob?.cancel()
        _state.update { it.copy(pendingUndo = null) }
    }

    // ── Requests ──────────────────────────────────────────────────────────

    /** "Connect" on a disconnected friend. */
    fun requestConnect(peer: PeerInfo) {
        setStatus(peer.id, PeerConnectionStatus.REQUEST_SENT)
        viewModelScope.launch {
            busy += peer.id
            val result = repository.requestConnect(peer.id)
            busy -= peer.id
            when (result) {
                is Result.Success -> {
                    val r = result.data
                    _state.update { s ->
                        s.copy(connections = s.connections.map {
                            if (it.id == peer.id) it.copy(status = r.status, requestId = r.requestId, cooldownUntilMs = r.cooldownUntilMs) else it
                        })
                    }
                    when (r.status) {
                        PeerConnectionStatus.CONNECTED -> showMessage("You and ${firstName(peer)} are connected again")
                        PeerConnectionStatus.COOLDOWN -> showMessage("${firstName(peer)} declined recently. Try again later.")
                        else -> {}
                    }
                }
                is Result.Error -> {
                    setStatus(peer.id, peer.status, peer.requestId)
                    showMessage(result.message)
                }
                else -> {}
            }
            loadConnections()
        }
    }

    /** Tap "Requested" to take the request back. */
    fun cancelRequest(peer: PeerInfo) {
        val id = peer.requestId ?: return loadConnections()
        setStatus(peer.id, PeerConnectionStatus.DISCONNECTED)
        runAction(peer.id, revertTo = peer, failMessage = "Couldn't cancel the request") {
            repository.cancelRequest(id)
        }
    }

    fun acceptRequest(requestId: String, peerId: String) {
        val peer = _state.value.connections.firstOrNull { it.id == peerId }
        setStatus(peerId, PeerConnectionStatus.CONNECTED)
        _state.update { s -> s.copy(requests = s.requests.filterNot { it.id == requestId }) }
        runAction(peerId, revertTo = peer, failMessage = "This request has expired") {
            repository.acceptRequest(requestId)
        }
    }

    fun declineRequest(requestId: String, peerId: String) {
        val peer = _state.value.connections.firstOrNull { it.id == peerId }
        setStatus(peerId, PeerConnectionStatus.DISCONNECTED)
        _state.update { s -> s.copy(requests = s.requests.filterNot { it.id == requestId }) }
        runAction(peerId, revertTo = peer, failMessage = "Couldn't decline the request") {
            repository.declineRequest(requestId)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun runAction(peerId: String, revertTo: PeerInfo?, failMessage: String, call: suspend () -> Result<*>): Job =
        viewModelScope.launch {
            busy += peerId
            val result = call()
            busy -= peerId
            if (result is Result.Error) {
                if (revertTo != null) setStatus(peerId, revertTo.status, revertTo.requestId)
                if (_state.value.pendingUndo?.peer?.id == peerId) dismissUndo()
                showMessage(failMessage)
            }
            loadConnections()
            loadRequests()
        }

    private fun setStatus(peerId: String, status: PeerConnectionStatus, requestId: String? = null) =
        _state.update { s ->
            s.copy(connections = s.connections.map {
                if (it.id == peerId) it.copy(status = status, requestId = requestId, cooldownUntilMs = null) else it
            })
        }

    private fun showMessage(text: String) = _state.update { it.copy(message = text) }

    private fun firstName(peer: PeerInfo) = peer.name.trim().substringBefore(' ').ifBlank { "your friend" }

    /** Send this phone's push token so connect requests can arrive as notifications. */
    private fun registerPushToken() {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                viewModelScope.launch { repository.registerPushToken(token) }
            }
        }.onFailure { Log.w("Connections", "push token unavailable: ${it.message}") }
    }

    class Factory(
        private val connectUseCase: ConnectUseCase,
        private val getConnectionsUseCase: GetConnectionsUseCase,
        private val tokenManager: TokenManager,
        private val repository: ConnectionsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConnectionsViewModel(connectUseCase, getConnectionsUseCase, tokenManager, repository) as T
    }

    companion object {
        /** How long the "Disconnected · Undo" snackbar stays (the server allows 15 s). */
        const val UNDO_SHOWN_MS = 5_000L
    }
}

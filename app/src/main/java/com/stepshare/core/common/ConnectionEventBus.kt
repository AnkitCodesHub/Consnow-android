package com.Consnow.core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A friend's connection changed (disconnect, connect, request sent/received/declined/cancelled).
 * Comes from the server's `connection_changed` WebSocket event or from a push notification.
 * Screens reload their friends list / requests when they see one.
 */
data class ConnectionChangedEvent(
    val peerId: String,
    val type: String,
    val requestId: String? = null,
    val peerName: String? = null,
)

object ConnectionEventBus {
    private val _events = MutableSharedFlow<ConnectionChangedEvent>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ConnectionChangedEvent> = _events.asSharedFlow()

    fun tryEmit(event: ConnectionChangedEvent) = _events.tryEmit(event)
}

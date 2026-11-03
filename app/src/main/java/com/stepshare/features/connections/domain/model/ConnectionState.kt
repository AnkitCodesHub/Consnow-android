package com.Consnow.features.connections.domain.model

/** How a friend row looks from my side. */
enum class PeerConnectionStatus {
    CONNECTED,        // both see each other's data — button: Disconnect
    DISCONNECTED,     // nothing shared — button: Connect
    REQUEST_SENT,     // I asked, waiting for them — button: Requested (tap to cancel)
    REQUEST_RECEIVED, // they asked me — buttons: Accept / Decline
    COOLDOWN;         // they declined me < 3 h ago — "Try again in …"

    companion object {
        fun fromServer(state: String?, status: String?): PeerConnectionStatus = when (state) {
            "connected" -> CONNECTED
            "disconnected" -> DISCONNECTED
            "request_sent" -> REQUEST_SENT
            "request_received" -> REQUEST_RECEIVED
            "cooldown" -> COOLDOWN
            // Older server without "state": active rows are connected, anything else not
            else -> if (status == null || status == "active") CONNECTED else DISCONNECTED
        }
    }
}

data class PeerInfo(
    val id: String,
    val name: String,
    val sharingEnabled: Boolean,
    val status: PeerConnectionStatus = PeerConnectionStatus.CONNECTED,
    val requestId: String? = null,     // set for REQUEST_SENT / REQUEST_RECEIVED
    val cooldownUntilMs: Long? = null, // set for COOLDOWN
) {
    val isConnected: Boolean get() = status == PeerConnectionStatus.CONNECTED
}

/** A Connect request waiting for my answer (requests inbox). */
data class IncomingRequest(
    val id: String,
    val fromId: String,
    val fromName: String,
    val expiresAtMs: Long,
)

/** Result of tapping Connect / Undo etc. */
data class ConnectActionResult(
    val status: PeerConnectionStatus,
    val requestId: String? = null,
    val cooldownUntilMs: Long? = null,
    val undoUntilMs: Long? = null,
)

/** Shown as the "Disconnected from X · Undo" snackbar for 5 seconds. */
data class PendingUndo(val peer: PeerInfo, val shownAtMs: Long)

data class ConnectionsState(
    val isLoading: Boolean = false,
    val myConnectionCode: String = "",
    val connections: List<PeerInfo> = emptyList(),
    val requests: List<IncomingRequest> = emptyList(),
    val error: String? = null,
    val connectSuccess: Boolean = false,
    val connectSuccessText: String = "Connected!",
    val pendingUndo: PendingUndo? = null,
    val message: String? = null, // one-shot snackbar text
)

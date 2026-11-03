package com.Consnow.core.network.dto

data class CreateConnectionRequest(val connectionCode: String)

/**
 * One friend row. The fields after createdAt only come back with
 * GET /connections?include=disconnected (older server builds leave them null).
 *
 * state: connected | disconnected | request_sent | request_received | cooldown
 */
data class ConnectionResponse(
    val id: String,
    val status: String,
    val peer: PeerDto,
    val createdAt: String,
    val state: String? = null,
    val requestId: String? = null,
    val requestExpiresAt: String? = null,
    val cooldownUntil: String? = null,
    val disconnectedByMe: Boolean? = null,
)

data class PeerDto(
    val id: String,
    val name: String,
    val sharingEnabled: Boolean
)

/** Reply to disconnect / undo / request. */
data class ConnectionActionResponse(
    val state: String? = null,
    val requestId: String? = null,
    val requestExpiresAt: String? = null,
    val undoUntil: String? = null,
    val cooldownUntil: String? = null,
)

/** One row of the requests inbox (GET /connections/requests). */
data class IncomingRequestResponse(
    val id: String,
    val from: RequestFromDto,
    val createdAt: String,
    val expiresAt: String,
)

data class RequestFromDto(val id: String, val name: String)

data class FcmTokenRequest(val token: String?)

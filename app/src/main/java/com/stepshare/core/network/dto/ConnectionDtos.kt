package com.Consnow.core.network.dto

data class CreateConnectionRequest(val connectionCode: String)

data class ConnectionResponse(
    val id: String,
    val status: String,
    val peer: PeerDto,
    val createdAt: String
)

data class PeerDto(
    val id: String,
    val name: String,
    val sharingEnabled: Boolean
)

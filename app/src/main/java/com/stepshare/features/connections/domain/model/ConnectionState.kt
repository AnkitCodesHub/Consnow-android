package com.Consnow.features.connections.domain.model

data class PeerInfo(
    val id: String,
    val name: String,
    val sharingEnabled: Boolean
)

data class ConnectionsState(
    val isLoading: Boolean = false,
    val myConnectionCode: String = "",
    val connections: List<PeerInfo> = emptyList(),
    val error: String? = null,
    val connectSuccess: Boolean = false
)

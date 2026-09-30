package com.Consnow.features.connections.domain

import com.Consnow.core.common.Result
import com.Consnow.features.connections.domain.model.PeerInfo

interface ConnectionsRepository {
    suspend fun connect(code: String): Result<PeerInfo>
    suspend fun getConnections(): Result<List<PeerInfo>>
}

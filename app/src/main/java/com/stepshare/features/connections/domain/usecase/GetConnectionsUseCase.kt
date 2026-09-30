package com.Consnow.features.connections.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.PeerInfo

class GetConnectionsUseCase(private val repository: ConnectionsRepository) {
    suspend operator fun invoke(): Result<List<PeerInfo>> {
        return repository.getConnections()
    }
}

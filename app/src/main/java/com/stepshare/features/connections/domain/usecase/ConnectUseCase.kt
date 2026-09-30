package com.Consnow.features.connections.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.PeerInfo

class ConnectUseCase(private val repository: ConnectionsRepository) {
    suspend operator fun invoke(code: String): Result<PeerInfo> {
        val cleaned = code.trim().uppercase()
        if (cleaned.length != 6) return Result.Error("Code must be exactly 6 characters")
        if (!cleaned.matches(Regex("^[A-Z0-9]+$"))) return Result.Error("Code contains invalid characters")
        return repository.connect(cleaned)
    }
}

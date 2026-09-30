package com.Consnow.features.connections.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.CreateConnectionRequest
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.PeerInfo

class ConnectionsRepositoryImpl(
    private val apiService: ApiService
) : ConnectionsRepository {

    override suspend fun connect(code: String): Result<PeerInfo> {
        return try {
            val response = apiService.createConnection(CreateConnectionRequest(code.uppercase()))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.Success(PeerInfo(body.peer.id, body.peer.name, body.peer.sharingEnabled))
            } else {
                Result.Error(response.errorBody()?.string() ?: "Connection failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    override suspend fun getConnections(): Result<List<PeerInfo>> {
        return try {
            val response = apiService.getConnections()
            if (response.isSuccessful && response.body() != null) {
                val peers = response.body()!!.map {
                    PeerInfo(it.peer.id, it.peer.name, it.peer.sharingEnabled)
                }
                Result.Success(peers)
            } else {
                Result.Error("Failed to load connections")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
}

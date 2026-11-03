package com.Consnow.features.connections.domain

import com.Consnow.core.common.Result
import com.Consnow.features.connections.domain.model.ConnectActionResult
import com.Consnow.features.connections.domain.model.IncomingRequest
import com.Consnow.features.connections.domain.model.PeerInfo

interface ConnectionsRepository {
    suspend fun connect(code: String): Result<PeerInfo>
    suspend fun getConnections(): Result<List<PeerInfo>>

    suspend fun disconnect(peerId: String): Result<ConnectActionResult>
    suspend fun undoDisconnect(peerId: String): Result<ConnectActionResult>
    suspend fun requestConnect(peerId: String): Result<ConnectActionResult>
    suspend fun getIncomingRequests(): Result<List<IncomingRequest>>
    suspend fun acceptRequest(requestId: String): Result<Unit>
    suspend fun declineRequest(requestId: String): Result<Unit>
    suspend fun cancelRequest(requestId: String): Result<Unit>
    suspend fun registerPushToken(token: String?): Result<Unit>
}

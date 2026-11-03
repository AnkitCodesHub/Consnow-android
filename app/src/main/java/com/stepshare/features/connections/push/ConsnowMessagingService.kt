package com.Consnow.features.connections.push

import android.util.Log
import com.Consnow.ConsnowApp
import com.Consnow.core.common.ConnectionChangedEvent
import com.Consnow.core.common.ConnectionEventBus
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives connect-request pushes from the server (data-only messages):
 *   type=connection_request  {requestId, peerId, peerName} → notification with Accept / Decline
 *   type=connection_accepted {peerId, peerName}            → "X accepted your request"
 */
class ConsnowMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val container = (application as ConsnowApp).appContainer
        scope.launch {
            // Only signed-in users have an account to attach the token to; otherwise
            // ConnectionsViewModel sends it after the next sign-in.
            if (container.tokenManager.getToken() != null) {
                container.connectionsRepository.registerPushToken(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val peerId = data["peerId"] ?: return
        val peerName = data["peerName"].orEmpty()
        when (data["type"]) {
            "connection_request" -> {
                val requestId = data["requestId"] ?: return
                ConnectionNotifications.showRequest(this, requestId, peerId, peerName)
                ConnectionEventBus.tryEmit(ConnectionChangedEvent(peerId, "request_received", requestId, peerName))
            }
            "connection_accepted" -> {
                ConnectionNotifications.showAccepted(this, peerId, peerName)
                ConnectionEventBus.tryEmit(ConnectionChangedEvent(peerId, "request_accepted", peerName = peerName))
            }
            else -> Log.d("ConsnowPush", "ignored push type=${data["type"]}")
        }
    }
}

package com.Consnow.features.connections.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.Consnow.ConsnowApp
import com.Consnow.core.common.ConnectionChangedEvent
import com.Consnow.core.common.ConnectionEventBus
import com.Consnow.core.common.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Handles the Accept / Decline buttons on a connect-request notification, without opening the app. */
class ConnectionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val peerId = intent.getStringExtra(EXTRA_PEER_ID).orEmpty()
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val accept = intent.action == ACTION_ACCEPT

        // Remove the buttons straight away so it can't be tapped twice
        ConnectionNotifications.cancel(context, notificationId)

        val repo = (context.applicationContext as ConsnowApp).appContainer.connectionsRepository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // A broadcast gets ~10 s; stay well inside it
                val result = withTimeoutOrNull(8_000) {
                    if (accept) repo.acceptRequest(requestId) else repo.declineRequest(requestId)
                }
                if (result is Result.Success) {
                    ConnectionEventBus.tryEmit(
                        ConnectionChangedEvent(peerId, if (accept) "connected" else "request_declined", requestId)
                    )
                    if (accept) {
                        val name = peerName.ifBlank { "your friend" }
                        ConnectionNotifications.showInfo(
                            context, notificationId,
                            "Connected with $name", "You can see each other's activity again.",
                        )
                    }
                } else {
                    ConnectionNotifications.showInfo(
                        context, notificationId,
                        "Couldn't ${if (accept) "accept" else "decline"} the request",
                        if (result == null) "No internet. Open Consnow to try again."
                        else "It may have expired. Open Consnow to see your requests.",
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.Consnow.action.ACCEPT_CONNECTION"
        const val ACTION_DECLINE = "com.Consnow.action.DECLINE_CONNECTION"
        const val EXTRA_REQUEST_ID = "requestId"
        const val EXTRA_PEER_ID = "peerId"
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}

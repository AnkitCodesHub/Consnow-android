package com.Consnow.core.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.common.PeerUnlockEvent
import com.Consnow.core.common.PeerLockEvent

class SocketManager(
    private val baseUrl: String,
    private val token: String,
) {
    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect() {
        Log.d(TAG, "connect() called with baseUrl=$baseUrl")
        if (socket?.connected() == true) return
        try {
            val opts = IO.Options.builder()
                .setAuth(mapOf("token" to token))
                .setReconnection(true)
                .setReconnectionAttempts(Int.MAX_VALUE)
                .setReconnectionDelay(3000)
                .build()

            socket = IO.socket(baseUrl, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected")
            }
            socket?.on(Socket.EVENT_DISCONNECT) { args ->
                Log.d(TAG, "Socket disconnected: ${args.firstOrNull()}")
            }
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connect error: ${args.firstOrNull()}")
            }
            socket?.on("peer_unlocked") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.getString("userId")
                    val unlockedAtMs = Instant.parse(data.getString("unlockedAt")).toEpochMilli()
                    scope.launch { ActivityEventBus.emit(PeerUnlockEvent(userId, unlockedAtMs)) }
                } catch (e: Exception) {
                    Log.e(TAG, "peer_unlocked parse error: ${e.message}")
                }
            }
            socket?.on("peer_locked") { args ->
                try {
                    val data = args[0] as JSONObject
                    val userId = data.getString("userId")
                    scope.launch { ActivityEventBus.emit(PeerLockEvent(userId)) }
                } catch (e: Exception) {
                    Log.e(TAG, "peer_locked parse error: ${e.message}")
                }
            }

            socket?.connect()  // ← always last
        } catch (e: Exception) {
            Log.e(TAG, "Socket init error: ${e.message}")
        }
    }
    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    companion object {
        private const val TAG = "SocketManager"
    }
}
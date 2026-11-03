//Ankit Pandey oct-29-2026
package com.Consnow.features.connections.data.repository

import android.content.Context
import com.Consnow.core.common.Result
import com.Consnow.core.common.toFriendlyMessage
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.ConnectionActionResponse
import com.Consnow.core.network.dto.ConnectionResponse
import com.Consnow.core.network.dto.CreateConnectionRequest
import com.Consnow.core.network.dto.FcmTokenRequest
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.ConnectActionResult
import com.Consnow.features.connections.domain.model.IncomingRequest
import com.Consnow.features.connections.domain.model.PeerConnectionStatus
import com.Consnow.features.connections.domain.model.PeerInfo
import com.Consnow.features.steps.data.cache.PeerProfileCache
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import java.time.Instant


/**
 * Default implementation of [ConnectionsRepository].
 *
 * Talks to the backend through [ApiService] to create and fetch connections, and keeps a
 * lightweight local copy of the connections list in [android.content.SharedPreferences] so the
 * list can still be shown when the device is offline or the API call fails.
 *
 * Consent rule: whenever a friend is not CONNECTED (we disconnected, or they did), their
 * cached steps / lock / last-active data is wiped from [PeerProfileCache] so it can't be
 * shown again, not even offline.
 *
 * @property apiService Retrofit service used for all connection related network calls.
 * @property context Context used to open the `connections_cache` SharedPreferences file.
 */
class ConnectionsRepositoryImpl(
    private val apiService: ApiService,
    private val context: Context
) : ConnectionsRepository {

    /** Private SharedPreferences file that stores the last successfully fetched peer list as JSON. */
    private val prefs = context.getSharedPreferences("connections_cache", Context.MODE_PRIVATE)

    /**
     * Creates a new connection with another user using their invite code.
     *
     * For someone you were connected with before and is now disconnected, the server sends
     * a Connect request instead; the returned [PeerInfo.status] tells which happened.
     */
    override suspend fun connect(code: String): Result<PeerInfo> {
        return try {
            val response = apiService.createConnection(CreateConnectionRequest(code.uppercase()))
            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!.toPeerInfo())
            } else {
                Result.Error(response.errorMessage("Connection failed"), response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }
    }

    /**
     * Fetches the current user's connections (including disconnected ones) from the server.
     *
     * On success, the fresh list is written to the local cache before it is returned.
     * If the server responds with an error, or the request throws (for example when offline),
     * the last cached list is returned instead so the UI does not go blank.
     */
    override suspend fun getConnections(): Result<List<PeerInfo>> {
        return try {
            val response = apiService.getConnections("disconnected")
            if (response.isSuccessful && response.body() != null) {
                val peers = response.body()!!.map { it.toPeerInfo() }
                // The other person may have disconnected while we were away: drop their data
                peers.filterNot { it.isConnected }.forEach { PeerProfileCache.remove(it.id) }
                saveToCache(peers)
                Result.Success(peers)
            } else {
                // API failed but we have a cache — return it silently
                val cached = loadFromCache()
                if (cached.isNotEmpty()) Result.Success(cached)
                else Result.Error("Failed to load connections")
            }
        } catch (e: Exception) {
            // Offline — return cache so the list doesn't go blank
            val cached = loadFromCache()
            if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun disconnect(peerId: String): Result<ConnectActionResult> =
        action { apiService.disconnect(peerId) }.also {
            if (it is Result.Success) {
                PeerProfileCache.remove(peerId)
                updateCachedStatus(peerId, PeerConnectionStatus.DISCONNECTED)
            }
        }

    override suspend fun undoDisconnect(peerId: String): Result<ConnectActionResult> =
        action { apiService.undoDisconnect(peerId) }

    override suspend fun requestConnect(peerId: String): Result<ConnectActionResult> {
        return try {
            val response = apiService.requestConnect(peerId)
            when {
                response.isSuccessful -> Result.Success(response.body().toResult())
                // Declined less than 3 h ago: not an error, the row shows "Try again in …"
                response.code() == 429 -> {
                    val until = response.errorJson()?.optString("cooldownUntil")?.toEpochMsOrNull()
                    Result.Success(ConnectActionResult(PeerConnectionStatus.COOLDOWN, cooldownUntilMs = until))
                }
                else -> Result.Error(response.errorMessage("Couldn't send request"), response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun getIncomingRequests(): Result<List<IncomingRequest>> = try {
        val response = apiService.getIncomingRequests()
        if (response.isSuccessful) {
            Result.Success(response.body().orEmpty().map {
                IncomingRequest(
                    id = it.id,
                    fromId = it.from.id,
                    fromName = it.from.name,
                    expiresAtMs = it.expiresAt.toEpochMsOrNull() ?: 0L,
                )
            })
        } else Result.Error(response.errorMessage("Couldn't load requests"), response.code())
    } catch (e: Exception) {
        Result.Error(e.toFriendlyMessage())
    }

    override suspend fun acceptRequest(requestId: String): Result<Unit> =
        unit { apiService.acceptRequest(requestId) }

    override suspend fun declineRequest(requestId: String): Result<Unit> =
        unit { apiService.declineRequest(requestId) }

    override suspend fun cancelRequest(requestId: String): Result<Unit> =
        unit { apiService.cancelRequest(requestId) }

    override suspend fun registerPushToken(token: String?): Result<Unit> =
        unit { apiService.registerPushToken(FcmTokenRequest(token)) }

    // ── helpers ────────────────────────────────────────────────────────────

    private suspend fun action(call: suspend () -> Response<ConnectionActionResponse>): Result<ConnectActionResult> =
        try {
            val response = call()
            if (response.isSuccessful) Result.Success(response.body().toResult())
            else Result.Error(response.errorMessage("Something went wrong"), response.code())
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }

    private suspend fun unit(call: suspend () -> Response<*>): Result<Unit> =
        try {
            val response = call()
            if (response.isSuccessful) Result.Success(Unit)
            else Result.Error(response.errorMessage("Something went wrong"), response.code())
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }

    private fun ConnectionResponse.toPeerInfo() = PeerInfo(
        id = peer.id,
        name = peer.name,
        sharingEnabled = peer.sharingEnabled,
        status = PeerConnectionStatus.fromServer(state, status),
        requestId = requestId,
        cooldownUntilMs = cooldownUntil?.toEpochMsOrNull(),
    )

    private fun ConnectionActionResponse?.toResult() = ConnectActionResult(
        status = PeerConnectionStatus.fromServer(this?.state, null),
        requestId = this?.requestId,
        cooldownUntilMs = this?.cooldownUntil?.toEpochMsOrNull(),
        undoUntilMs = this?.undoUntil?.toEpochMsOrNull(),
    )

    private fun String.toEpochMsOrNull(): Long? =
        takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

    private fun Response<*>.errorJson(): JSONObject? =
        runCatching { JSONObject(errorBody()?.string().orEmpty()) }.getOrNull()

    /** Server errors look like {"message": "..."}; show only the message. */
    private fun Response<*>.errorMessage(fallback: String): String =
        errorJson()?.optString("message")?.takeIf { it.isNotBlank() } ?: fallback

    private fun updateCachedStatus(peerId: String, status: PeerConnectionStatus) {
        val cached = loadFromCache()
        if (cached.isEmpty()) return
        saveToCache(cached.map { if (it.id == peerId) it.copy(status = status, requestId = null) else it })
    }

    /**
     * Serializes the given peers to a JSON array and stores it under the `peers` key,
     * replacing whatever was cached before.
     */
    private fun saveToCache(peers: List<PeerInfo>) {
        val arr = JSONArray()
        peers.forEach { peer ->
            val obj = JSONObject()
            obj.put("id", peer.id)
            obj.put("name", peer.name)
            obj.put("sharingEnabled", peer.sharingEnabled)
            obj.put("status", peer.status.name)
            peer.requestId?.let { obj.put("requestId", it) }
            peer.cooldownUntilMs?.let { obj.put("cooldownUntilMs", it) }
            arr.put(obj)
        }
        prefs.edit().putString("peers", arr.toString()).apply()
    }

    /**
     * Reads the cached peers back from SharedPreferences.
     * Entries written by older app versions have no status and count as connected.
     */
    private fun loadFromCache(): List<PeerInfo> {
        val json = prefs.getString("peers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PeerInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    sharingEnabled = obj.getBoolean("sharingEnabled"),
                    status = runCatching { PeerConnectionStatus.valueOf(obj.optString("status")) }
                        .getOrDefault(PeerConnectionStatus.CONNECTED),
                    requestId = obj.optString("requestId").ifEmpty { null },
                    cooldownUntilMs = if (obj.has("cooldownUntilMs")) obj.getLong("cooldownUntilMs") else null,
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}

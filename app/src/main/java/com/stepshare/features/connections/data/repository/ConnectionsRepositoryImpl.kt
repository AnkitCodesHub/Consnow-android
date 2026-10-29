//Ankit Pandey oct-29-2026
package com.Consnow.features.connections.data.repository

import android.content.Context
import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.CreateConnectionRequest
import com.Consnow.features.connections.domain.ConnectionsRepository
import com.Consnow.features.connections.domain.model.PeerInfo
import org.json.JSONArray


/**
 * Default implementation of [ConnectionsRepository].
 *
 * Talks to the backend through [ApiService] to create and fetch connections, and keeps a
 * lightweight local copy of the connections list in [android.content.SharedPreferences] so the
 * list can still be shown when the device is offline or the API call fails.
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
     * The code is uppercased before it is sent, so users can type it in any case.
     *
     * @param code The invite code shared by the other user.
     * @return [Result.Success] with the connected peer's [PeerInfo] when the server accepts the code,
     * or [Result.Error] with the server's error body and HTTP status code when it rejects it.
     * Network or parsing failures return [Result.Error] with the exception message.
     */
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

    /**
     * Fetches the current user's connections from the server.
     *
     * On success, the fresh list is written to the local cache before it is returned.
     * If the server responds with an error, or the request throws (for example when offline),
     * the last cached list is returned instead so the UI does not go blank.
     *
     * @return [Result.Success] with the list of peers, either fresh from the API or from the cache.
     * Returns [Result.Error] only when the request fails and the cache is empty.
     */
    override suspend fun getConnections(): Result<List<PeerInfo>> {
        return try {
            val response = apiService.getConnections()
            if (response.isSuccessful && response.body() != null) {
                val peers = response.body()!!.map {
                    PeerInfo(it.peer.id, it.peer.name, it.peer.sharingEnabled)
                }
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
            else Result.Error(e.message ?: "Network error")
        }
    }

    /**
     * Serializes the given peers to a JSON array and stores it under the `peers` key,
     * replacing whatever was cached before.
     *
     * Uses [android.content.SharedPreferences.Editor.apply], so the write happens asynchronously.
     *
     * @param peers The list of peers to cache.
     */
    private fun saveToCache(peers: List<PeerInfo>) {
        val arr = JSONArray()
        peers.forEach { peer ->
            val obj = org.json.JSONObject()
            obj.put("id", peer.id)
            obj.put("name", peer.name)
            obj.put("sharingEnabled", peer.sharingEnabled)
            arr.put(obj)
        }
        prefs.edit().putString("peers", arr.toString()).apply()
    }

    /**
     * Reads the cached peers back from SharedPreferences.
     *
     * @return The cached list of [PeerInfo], or an empty list when nothing is cached
     * or the stored JSON cannot be parsed.
     */
    private fun loadFromCache(): List<PeerInfo> {
        val json = prefs.getString("peers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                PeerInfo(obj.getString("id"), obj.getString("name"), obj.getBoolean("sharingEnabled"))
            }
        } catch (_: Exception) { emptyList() }
    }
}
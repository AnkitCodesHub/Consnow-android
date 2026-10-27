package com.Consnow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "Consnow_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("jwt_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val CONNECTION_CODE_KEY = stringPreferencesKey("connection_code")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data.map { it[TOKEN_KEY] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[USER_ID_KEY] }
    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[USER_NAME_KEY] }
    val connectionCodeFlow: Flow<String?> = context.dataStore.data.map { it[CONNECTION_CODE_KEY] }

    suspend fun getToken(): String? = tokenFlow.first()

    /** Issued-at time from the JWT payload, in epoch ms (null if unreadable). */
    fun issuedAtMs(token: String): Long? = try {
        val payload = token.split(".")[1]
        val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP))
        org.json.JSONObject(json).optLong("iat", 0L).takeIf { it > 0 }?.times(1000)
    } catch (_: Exception) { null }
    suspend fun getUserId(): String? = userIdFlow.first()

    suspend fun saveSession(token: String, userId: String, userName: String, connectionCode: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs[USER_ID_KEY] = userId
            prefs[USER_NAME_KEY] = userName
            prefs[CONNECTION_CODE_KEY] = connectionCode
        }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[TOKEN_KEY] = token }
    }

    /**
     * Clears the session only if [expiredToken] is still the current token.
     * Stops a late 401 from an old request wiping a token that was just refreshed.
     */
    suspend fun clearSessionIfToken(expiredToken: String) {
        context.dataStore.edit { if (it[TOKEN_KEY] == expiredToken) it.clear() }
    }

    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }
}

package com.Consnow.features.auth.data.repository

import com.Consnow.features.steps.data.cache.PeerProfileCache
import com.Consnow.core.common.Result
import com.Consnow.core.common.toFriendlyMessage
import com.Consnow.core.datastore.TokenManager
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.LoginRequest
import com.Consnow.core.network.dto.RegisterRequest
import com.Consnow.features.auth.domain.AuthRepository
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) : AuthRepository {

    override suspend fun register(name: String, email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.register(RegisterRequest(name, email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveSession(
                    token = body.token,
                    userId = body.user.id,
                    userName = body.user.name,
                    connectionCode = body.user.connectionCode
                )
                FirebaseAnalytics.getInstance(context).apply {
                    setUserId(body.user.id)
                    setUserProperty("name", body.user.name)
                    setUserProperty("email", email)
                }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Registration failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveSession(
                    token = body.token,
                    userId = body.user.id,
                    userName = body.user.name,
                    connectionCode = body.user.connectionCode
                )

                // for getting analytics on google firebase
                FirebaseAnalytics.getInstance(context).apply {
                    setUserId(body.user.id)
                    setUserProperty("name", body.user.name)
                    setUserProperty("email", email)
                }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Login failed", response.code())
            }
        } catch (e: Exception) {
            Result.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun logout() {
        tokenManager.clearSession()
        PeerProfileCache.clear() // next account on this phone must not see old friends' data
        FirebaseAnalytics.getInstance(context).setUserId(null)
    }

    override suspend fun isLoggedIn(): Boolean = tokenManager.getToken() != null

    override fun observeLoggedIn(): Flow<Boolean> =
        tokenManager.tokenFlow.map { it != null }.distinctUntilChanged()

    override suspend fun refreshTokenIfStale() {
        val token = tokenManager.getToken() ?: return
        val issuedAt = tokenManager.issuedAtMs(token)
        if (issuedAt != null && System.currentTimeMillis() - issuedAt < REFRESH_AFTER_MS) return
        try {
            val response = apiService.refreshToken()
            val fresh = response.body()?.token
            if (response.isSuccessful && fresh != null) tokenManager.saveToken(fresh)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // offline: try again on the next call
        }
    }

    private companion object {
        const val REFRESH_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}

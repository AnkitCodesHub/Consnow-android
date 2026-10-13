package com.Consnow.features.auth.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.datastore.TokenManager
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.LoginRequest
import com.Consnow.core.network.dto.RegisterRequest
import com.Consnow.features.auth.domain.AuthRepository
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics

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
            Result.Error(e.message ?: "Network error")
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
            Result.Error(e.message ?: "Network error")
        }
    }

    override suspend fun logout() {
        tokenManager.clearSession()
        FirebaseAnalytics.getInstance(context).setUserId(null)
    }

    override suspend fun isLoggedIn(): Boolean = tokenManager.getToken() != null
}

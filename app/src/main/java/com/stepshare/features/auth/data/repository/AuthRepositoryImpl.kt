package com.Consnow.features.auth.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.datastore.TokenManager
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.LoginRequest
import com.Consnow.core.network.dto.RegisterRequest
import com.Consnow.features.auth.domain.AuthRepository

class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
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
    }

    override suspend fun isLoggedIn(): Boolean = tokenManager.getToken() != null
}

package com.Consnow.features.auth.domain

import com.Consnow.core.common.Result

interface AuthRepository {
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean
}

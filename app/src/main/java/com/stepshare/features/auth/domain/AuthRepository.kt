package com.Consnow.features.auth.domain

import com.Consnow.core.common.Result
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun register(name: String, email: String, password: String): Result<Unit>
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun isLoggedIn(): Boolean

    /** Emits false as soon as the session is cleared (logout or expired token). */
    fun observeLoggedIn(): Flow<Boolean>

    /** Renews the JWT if it is older than a day. No-op when offline or fresh. */
    suspend fun refreshTokenIfStale()
}

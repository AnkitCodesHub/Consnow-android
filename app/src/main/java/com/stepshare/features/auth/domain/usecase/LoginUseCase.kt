package com.Consnow.features.auth.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.auth.domain.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): Result<Unit> {
        if (email.isBlank()) return Result.Error("Email is required")
        if (password.length < 8) return Result.Error("Password must be at least 8 characters")
        return repository.login(email.trim().lowercase(), password)
    }
}

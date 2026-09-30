package com.Consnow.features.auth.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.auth.domain.AuthRepository

class RegisterUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(name: String, email: String, password: String): Result<Unit> {
        if (name.isBlank()) return Result.Error("Name is required")
        if (email.isBlank()) return Result.Error("Email is required")
        if (password.length < 8) return Result.Error("Password must be at least 8 characters")
        return repository.register(name.trim(), email.trim().lowercase(), password)
    }
}

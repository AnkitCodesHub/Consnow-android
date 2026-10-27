package com.Consnow.core.network.dto

data class RegisterRequest(val name: String, val email: String, val password: String)
data class LoginRequest(val email: String, val password: String)

data class TokenResponse(val token: String)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val name: String,
    val email: String,
    val connectionCode: String,
    val sharingEnabled: Boolean
)

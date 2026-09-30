package com.Consnow.features.auth.domain.model

data class AuthState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userId: String? = null,
    val userName: String? = null,
    val connectionCode: String? = null,
    val error: String? = null
)

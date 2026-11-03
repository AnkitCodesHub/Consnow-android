package com.Consnow.core.network.dto

/** One time a friend opened your profile: GET /profile-views */
data class ProfileViewResponse(
    val viewerId: String,
    val name: String,
    val viewedAt: String, // ISO time of that open
)

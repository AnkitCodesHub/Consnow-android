package com.Consnow.core.network.dto

data class SyncStepsRequest(
    val stepCount: Int,
    val date: String,
    val isActive: Boolean = false,
    // Device epoch ms of the last step in this count. Lets the server stamp
    // lastActiveAt with when the user walked, not when the phone got back online.
    val lastStepAt: Long? = null,
)

data class MyStepsResponse(
    val date: String,
    val stepCount: Int,
    val updatedAt: String?,
    val isActive: Boolean = false,
    val lastActiveAt: String? = null
)

data class PeerStepsResponse(
    val userId: String,
    val userName: String,
    val date: String,
    val stepCount: Int,
    val updatedAt: String?,
    val isActive: Boolean = false,
    val lastActiveAt: String? = null
)

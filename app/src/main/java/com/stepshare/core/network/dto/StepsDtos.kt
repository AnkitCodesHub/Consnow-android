package com.Consnow.core.network.dto

data class SyncStepsRequest(val stepCount: Int, val date: String, val isActive : Boolean = false)

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

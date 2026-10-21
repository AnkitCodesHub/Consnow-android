package com.Consnow.core.network.dto

data class UnlockResponse(
    val success: Boolean,       // whether operation succeeded
    val unlockedAt: String,     // timestamp when phone was unlocked
)

data class LockResponse(
    val success: Boolean,       // whether operation succeeded
)

data class LockEventRequest(
    val occurredAt: Long,       // epoch millis — sent with every unlock/lock so backend
    // stores the real time even if request was queued offline
)

data class PeerActivityResponse(
    val userId: String,         // peer's user ID
    val unlockedAt: String?,    // last unlock time, null if never unlocked
    val isLocked: Boolean,      // whether peer is currently locked
    val isOnline: Boolean,      // false if no heartbeat received in last 90s — peer went offline
)

data class OwnActivityResponse(
    val unlockedAt: String?,    // your last unlock time
    val isLocked: Boolean,      // your current lock status
    val isOnline: Boolean,      // your own online state (heartbeat freshness)
)
data class ActivityEventRequest(val occurredAt: Long) // epoch ms, captured at the broadcast
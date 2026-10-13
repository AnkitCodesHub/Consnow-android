package com.Consnow.core.network.dto

data class UnlockResponse(
    val success: Boolean, //operation sucess
    val unlockedAt: String, //date and time when phone unlocked
)

data class LockResponse(
    val success: Boolean, // whether operation succeded
)

data class PeerActivityResponse(
    val userId: String, //peer's ID
    val unlockedAt: String?, //their last unlock time  ?? null if never unlocked
    val isLocked: Boolean, //whether they are locked or not
)

data class OwnActivityResponse(
    val unlockedAt: String?, //last unlock time
    val isLocked: Boolean, // your recod status
)
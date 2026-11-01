
//Aankit Raj  Date:- 21 oct
//Handles peer unlocks , peer locks,and your own lock status
package com.Consnow.core.common

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PeerUnlockEvent(val userId: String, val unlockedAtMs: Long) // peer's id and their unlock timestamp
data class PeerLockEvent(val userId: String, val lockedAtMs: Long? = null) // peer's id and optional lock timestamp
data class OwnLockStatusEvent(val isLocked: Boolean, val timestamp: Long) // your own lock status and timestamp

object ActivityEventBus {
    private val _peerUnlocked = MutableSharedFlow<PeerUnlockEvent>(
        replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val peerUnlocked: SharedFlow<PeerUnlockEvent> = _peerUnlocked.asSharedFlow()

    private val _peerLocked = MutableSharedFlow<PeerLockEvent>(
        replay = 1, extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val peerLocked: SharedFlow<PeerLockEvent> = _peerLocked.asSharedFlow()

    private val _ownLockStatus = MutableSharedFlow<OwnLockStatusEvent>(
        replay = 1, extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val ownLockStatus: SharedFlow<OwnLockStatusEvent> = _ownLockStatus.asSharedFlow()

    suspend fun emit(event: PeerUnlockEvent) = _peerUnlocked.emit(event)
    suspend fun emit(event: PeerLockEvent) = _peerLocked.emit(event)
    suspend fun emitOwnLockStatus(event: OwnLockStatusEvent) = _ownLockStatus.emit(event)
}
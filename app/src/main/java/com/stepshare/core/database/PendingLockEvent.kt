package com.Consnow.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LockEventType { UNLOCK, LOCK }

@Entity(tableName = "pending_lock_events")
data class PendingLockEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: LockEventType,
    val occurredAt: Long,
    val synced: Boolean = false
)
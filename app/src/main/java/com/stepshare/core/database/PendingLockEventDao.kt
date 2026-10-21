package com.Consnow.core.database

import androidx.room.*

@Dao
interface PendingLockEventDao {

    @Insert
    suspend fun insert(event: PendingLockEvent): Long

    @Query("SELECT * FROM pending_lock_events WHERE synced = 0 ORDER BY occurredAt ASC")
    suspend fun getUnsynced(): List<PendingLockEvent>

    @Query("UPDATE pending_lock_events SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("DELETE FROM pending_lock_events WHERE synced = 1")
    suspend fun deleteAllSynced()
}
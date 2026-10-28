package com.Consnow.core.database.dao

import androidx.room.*
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.core.database.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface StepRecordDao {

    @Query("SELECT * FROM step_records WHERE user_id = :userId AND date = :date LIMIT 1")
    suspend fun getRecord(userId: String, date: String): StepRecordEntity?

    @Query("SELECT * FROM step_records WHERE user_id = :userId AND date = :date LIMIT 1")
    fun observeRecord(userId: String, date: String): Flow<StepRecordEntity?>

    /**
     * Upsert: insert or replace on conflict (user_id + date unique index).
     * This is the idempotency mechanism — same user+date always overwrites.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: StepRecordEntity): Long

    // Oldest day first, so a backlog from a long offline period is replayed in order.
    @Query("SELECT * FROM step_records WHERE sync_status = :status ORDER BY date ASC")
    suspend fun getRecordsByStatus(status: SyncStatus): List<StepRecordEntity>

    @Query("UPDATE step_records SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    /**
     * Marks a record synced ONLY if its count is still the one we sent.
     * If new steps landed while the request was in flight, the row is left
     * PENDING so the newer count goes out on the next sync. Returns rows updated.
     */
    @Query("UPDATE step_records SET sync_status = :status WHERE id = :id AND step_count = :sentCount")
    suspend fun markSyncedIfUnchanged(id: Long, sentCount: Int, status: SyncStatus): Int

    @Query("UPDATE step_records SET step_count = :count, last_updated_at = :timestamp, sync_status = :status WHERE user_id = :userId AND date = :date")
    suspend fun updateStepCount(userId: String, date: String, count: Int, timestamp: Long, status: SyncStatus)
}

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

    @Query("SELECT * FROM step_records WHERE date = :date LIMIT 1")
    suspend fun getRecordByDate(date: String): StepRecordEntity?

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(record: StepRecordEntity): Long

    /** Raises the count; never lowers it. One statement, so concurrent writers can't undo each other. */
    @Query("UPDATE step_records SET step_count = :count, last_updated_at = :timestamp, sync_status = :status WHERE user_id = :userId AND date = :date AND step_count < :count")
    suspend fun raiseStepCount(userId: String, date: String, count: Int, timestamp: Long, status: SyncStatus): Int

    /** Same, but keeps last_updated_at (the time of the last real step). */
    @Query("UPDATE step_records SET step_count = :count, sync_status = :status WHERE user_id = :userId AND date = :date AND step_count < :count")
    suspend fun raiseStepCountKeepTime(userId: String, date: String, count: Int, status: SyncStatus): Int

    @Query("SELECT * FROM step_records WHERE user_id = :userId AND sync_status = :status ORDER BY date ASC")
    suspend fun getRecordsByStatusForUser(userId: String, status: SyncStatus): List<StepRecordEntity>

    @Query("DELETE FROM step_records WHERE date > :date")
    suspend fun deleteRecordsAfter(date: String): Int
}

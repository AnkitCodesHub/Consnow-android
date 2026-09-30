package com.Consnow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SyncStatus { SYNCED, PENDING }

@Entity(
    tableName = "step_records",
    indices = [Index(value = ["user_id", "date"], unique = true)]
)
data class StepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "step_count") val stepCount: Int,
    val date: String,                        // YYYY-MM-DD
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING
)

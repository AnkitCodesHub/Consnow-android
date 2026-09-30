package com.Consnow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.Consnow.core.database.dao.StepRecordDao
import com.Consnow.core.database.entity.StepRecordEntity

@Database(
    entities = [StepRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ConsnowDatabase : RoomDatabase() {
    abstract fun stepRecordDao(): StepRecordDao
}

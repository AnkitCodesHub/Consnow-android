package com.Consnow.features.steps.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.database.dao.StepRecordDao
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.SyncStepsRequest
import com.Consnow.features.steps.domain.StepsRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StepsRepositoryImpl(
    private val stepRecordDao: StepRecordDao,
    private val apiService: ApiService
) : StepsRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun observeTodaySteps(userId: String): Flow<StepRecordEntity?> {
        return stepRecordDao.observeRecord(userId, today())
    }

    override suspend fun updateLocalSteps(userId: String, stepCount: Int) {
        val date = today()
        val existing = stepRecordDao.getRecord(userId, date)

        if (existing != null) {
            if (stepCount > existing.stepCount) {
                stepRecordDao.updateStepCount(
                    userId = userId,
                    date = date,
                    count = stepCount,
                    timestamp = System.currentTimeMillis(),
                    status = SyncStatus.PENDING
                )
            }
        } else {
            stepRecordDao.upsert(
                StepRecordEntity(
                    userId = userId,
                    stepCount = stepCount,
                    date = date,
                    syncStatus = SyncStatus.PENDING
                )
            )
        }
    }

    override suspend fun syncPendingToServer(): Result<Int> {
        val pending = stepRecordDao.getRecordsByStatus(SyncStatus.PENDING)
        if (pending.isEmpty()) return Result.Success(0)

        var synced = 0
        for (record in pending) {
            try {
                val response = apiService.syncSteps(
                    SyncStepsRequest(stepCount = record.stepCount, date = record.date)
                )
                if (response.isSuccessful) {
                    stepRecordDao.updateSyncStatus(record.id, SyncStatus.SYNCED)
                    synced++
                }
            } catch (_: Exception) { }
        }
        return Result.Success(synced)
    }

    override suspend fun getPeerSteps(peerId: String): Result<PeerStepsResult> {
        return try {
            val response = apiService.getPeerSteps(peerId, today())
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.Success(PeerStepsResult(body.userName, body.stepCount, body.date))
            } else {
                Result.Error("Could not load steps")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    private fun today(): String = LocalDate.now().format(dateFormatter)
}

data class PeerStepsResult(
    val userName: String,
    val stepCount: Int,
    val date: String
)

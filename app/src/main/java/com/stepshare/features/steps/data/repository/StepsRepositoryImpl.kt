package com.Consnow.features.steps.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.database.dao.StepRecordDao
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.SyncStepsRequest
import com.Consnow.features.steps.domain.StepsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.Consnow.features.steps.domain.MyStepsResult

class StepsRepositoryImpl(
    private val stepRecordDao: StepRecordDao,
    private val apiService: ApiService
) : StepsRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /**
     * Follows "today" as the date changes. Previously the date was read once when the
     * Home screen opened, so after midnight Home kept showing yesterday's row and never
     * updated again until the app process died (which is what reinstalling did).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTodaySteps(userId: String): Flow<StepRecordEntity?> =
        currentDateFlow().flatMapLatest { date -> stepRecordDao.observeRecord(userId, date) }

    // Emits the local date now and again whenever it changes (midnight, time zone change).
    private fun currentDateFlow(): Flow<String> = flow {
        while (true) {
            emit(today())
            delay(30_000)
        }
    }.distinctUntilChanged()

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

    // The 15s loop, the debounced step sync and SyncWorker all call this. When the
    // network comes back they fire together; serialize them so requests leave in order.
    private val syncMutex = Mutex()

    override suspend fun syncPendingToServer(isActive: Boolean): Result<Int> = syncMutex.withLock {
        val pending = stepRecordDao.getRecordsByStatus(SyncStatus.PENDING)
        if (pending.isEmpty()) return@withLock Result.Success(0)

        val today = today()
        var synced = 0
        for (record in pending) {
            val response = try {
                apiService.syncSteps(
                    SyncStepsRequest(
                        stepCount = record.stepCount,
                        date = record.date,
                        // A past day's backlog is never "walking now"
                        isActive = isActive && record.date == today,
                        lastStepAt = record.lastUpdatedAt,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break // still offline: stop here, everything stays PENDING for the next run
            }

            if (response.isSuccessful) {
                // Only clear PENDING if no newer steps were written during the request.
                if (stepRecordDao.markSyncedIfUnchanged(record.id, record.stepCount, SyncStatus.SYNCED) > 0) {
                    synced++
                }
            } else if (response.code() == 401) {
                break // token expired: keep data PENDING until the user logs in again
            }
        }
        Result.Success(synced)
    }

    override suspend fun getPeerSteps(peerId: String): Result<PeerStepsResult> {
        return try {
            val response = apiService.getPeerSteps(peerId, today())
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.Success(
                    PeerStepsResult(
                        body.userName,
                        body.stepCount,
                        body.date,
                        body.isActive,
                        body.lastActiveAt
                    )
                )
            } else {
                Result.Error("Could not load steps")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }
        override suspend fun getMySteps(date: String): MyStepsResult? {
            return try {
                val response = apiService.getMySteps(date)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    MyStepsResult(
                        date = body.date,
                        stepCount = body.stepCount,
                        isActive = body.isActive,
                        lastActiveAt = body.lastActiveAt
                    )
                } else null
            } catch (_: Exception) { null }
        }

    private fun today(): String = LocalDate.now().format(dateFormatter)
}

data class PeerStepsResult(
    val userName: String,
    val stepCount: Int,
    val date: String,
    val isActive: Boolean = false,
    val lastActiveAt: String? = null
)

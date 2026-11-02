package com.Consnow.features.steps.data.repository

import com.Consnow.core.common.Result
import com.Consnow.core.common.TrustedClock
import com.Consnow.core.common.toFriendlyMessage
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
    private val apiService: ApiService,
    // Account currently logged in: only its rows are ever sent with its token
    private val currentUserId: suspend () -> String?,
    // False while the login merge is running (StepCounterService.isServerMergePending)
    private val canSync: () -> Boolean = { true },
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

    // Raise-only and atomic, so sensor writes and the login merge can't undo each other
    override suspend fun updateLocalSteps(userId: String, stepCount: Int, date: String, stepAtMs: Long) {
        if (date > today()) return // never store a day that hasn't happened yet
        stepRecordDao.insertIfAbsent(
            StepRecordEntity(
                userId = userId,
                stepCount = stepCount,
                date = date,
                lastUpdatedAt = stepAtMs,
                syncStatus = SyncStatus.PENDING,
            )
        )
        stepRecordDao.raiseStepCount(userId, date, stepCount, stepAtMs, SyncStatus.PENDING)
    }

    // The 15s loop, the debounced step sync and SyncWorker all call this. When the
    // network comes back they fire together; serialize them so requests leave in order.
    private val syncMutex = Mutex()

    // What the server was last told about "walking now". Lets us send the
    // walking -> stopped change once, instead of a request on every 15s tick.
    @Volatile private var serverThinksActive = false

    override suspend fun syncPendingToServer(isActive: Boolean): Result<Int> = syncMutex.withLock {
        // Wait for the login merge, or the server could take in steps it's about to be added to
        if (!canSync()) return@withLock Result.Success(0)
        val userId = currentUserId() ?: return@withLock Result.Success(0)
        val today = today()
        // Only this account's rows (another account may have used this phone), never a future day
        val pending = stepRecordDao.getRecordsByStatusForUser(userId, SyncStatus.PENDING)
            .filter { it.date <= today }

        // When walking stops (isActive=false) but today's record is already SYNCED,
        // we still need to tell the server so it clears the "Walking" status for peers.
        // Re-sync today's record with isActive=false.
        if (pending.isEmpty()) {
            if (!isActive && serverThinksActive) {
                sendActivityUpdate(userId, isActive = false)
            }
            return@withLock Result.Success(0)
        }

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
            } catch (e: Exception) {
                android.util.Log.e("StepSync", "Sync network error: ${e.javaClass.simpleName}: ${e.message}")
                break
            }

            if (response.isSuccessful) {
                if (record.date == today) serverThinksActive = isActive
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

    /**
     * Sends a lightweight sync with just the isActive flag for today's record.
     * Used when walking stops but there are no pending step changes to sync —
     * without this, the server would keep showing "Walking" until the next
     * step change triggers a real sync.
     */
    private suspend fun sendActivityUpdate(userId: String, isActive: Boolean) {
        try {
            val today = today()
            val record = stepRecordDao.getRecord(userId, today) ?: return
            val response = apiService.syncSteps(
                SyncStepsRequest(
                    stepCount = record.stepCount,
                    date = today,
                    isActive = isActive,
                    lastStepAt = record.lastUpdatedAt,
                )
            )
            android.util.Log.d("StepSync", "Walking-stopped update sent: HTTP ${response.code()}, isActive=$isActive")
            if (response.isSuccessful) serverThinksActive = isActive
        } catch (e: Exception) {
            // Best-effort: the periodic sync will catch it eventually
            android.util.Log.w("StepSync", "Walking-stopped update failed: ${e.message}")
        }
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
            Result.Error(e.toFriendlyMessage())
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

    override suspend fun fetchServerStepCount(date: String): Int? = try {
        val response = apiService.getMySteps(date)
        val body = response.body()
        if (response.isSuccessful && body != null && body.date == date) body.stepCount else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    override suspend fun mergeLocalSteps(userId: String, date: String, stepCount: Int, needsSync: Boolean, stepAtMs: Long) {
        if (date > today()) return
        val status = if (needsSync) SyncStatus.PENDING else SyncStatus.SYNCED
        stepRecordDao.insertIfAbsent(
            StepRecordEntity(userId = userId, stepCount = stepCount, date = date, lastUpdatedAt = stepAtMs, syncStatus = status)
        )
        // Keeps the row's time of the last real step, so "last moved" doesn't jump to now
        stepRecordDao.raiseStepCountKeepTime(userId, date, stepCount, status)
    }

    override suspend fun deleteFutureRecords(today: String) {
        val removed = stepRecordDao.deleteRecordsAfter(today)
        if (removed > 0) android.util.Log.w("StepSync", "Deleted $removed future-dated step rows")
    }

    // Server-corrected date, so a phone clock set ahead can't create rows for future days
    private fun today(): String = TrustedClock.today()
}

data class PeerStepsResult(
    val userName: String,
    val stepCount: Int,
    val date: String,
    val isActive: Boolean = false,
    val lastActiveAt: String? = null
)

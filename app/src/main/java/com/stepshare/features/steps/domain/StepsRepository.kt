package com.Consnow.features.steps.domain

import com.Consnow.core.common.Result
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.features.steps.data.repository.PeerStepsResult
import kotlinx.coroutines.flow.Flow

interface StepsRepository {
    fun observeTodaySteps(userId: String): Flow<StepRecordEntity?>
    suspend fun updateLocalSteps(userId: String, stepCount: Int, date: String, stepAtMs: Long)
    suspend fun syncPendingToServer(isActive: Boolean = false): Result<Int>
    suspend fun getPeerSteps(peerId: String): Result<PeerStepsResult>

    suspend fun getMySteps(date: String): MyStepsResult?

    /** The server's count for [date], or null if it couldn't be fetched. */
    suspend fun fetchServerStepCount(date: String): Int?

    /** Raises the local row for [date] to [stepCount] after the login merge (never lowers it). */
    suspend fun mergeLocalSteps(userId: String, date: String, stepCount: Int, needsSync: Boolean, stepAtMs: Long)

    /** Deletes rows dated after [today] (written while the phone clock was set ahead). */
    suspend fun deleteFutureRecords(today: String)
}
data class MyStepsResult(
    val date: String,
    val stepCount: Int,
    val isActive: Boolean,
    val lastActiveAt: String?
)

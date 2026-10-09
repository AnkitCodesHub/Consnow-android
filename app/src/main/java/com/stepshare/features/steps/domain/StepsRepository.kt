package com.Consnow.features.steps.domain

import com.Consnow.core.common.Result
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.features.steps.data.repository.PeerStepsResult
import kotlinx.coroutines.flow.Flow

interface StepsRepository {
    fun observeTodaySteps(userId: String): Flow<StepRecordEntity?>
    suspend fun updateLocalSteps(userId: String, stepCount: Int)
    suspend fun syncPendingToServer(isActive: Boolean = false): Result<Int>
    suspend fun getPeerSteps(peerId: String): Result<PeerStepsResult>

    suspend fun getMySteps(date: String): MyStepsResult?
}
data class MyStepsResult(
    val date: String,
    val stepCount: Int,
    val isActive: Boolean,
    val lastActiveAt: String?
)

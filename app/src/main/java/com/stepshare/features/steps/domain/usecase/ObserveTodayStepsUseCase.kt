package com.Consnow.features.steps.domain.usecase

import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.features.steps.domain.StepsRepository
import kotlinx.coroutines.flow.Flow

class ObserveTodayStepsUseCase(private val repository: StepsRepository) {
    operator fun invoke(userId: String): Flow<StepRecordEntity?> {
        return repository.observeTodaySteps(userId)
    }
}

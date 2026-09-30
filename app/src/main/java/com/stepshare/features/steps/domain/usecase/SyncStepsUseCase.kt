package com.Consnow.features.steps.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.steps.domain.StepsRepository

class SyncStepsUseCase(private val repository: StepsRepository) {
    suspend operator fun invoke(): Result<Int> {
        return repository.syncPendingToServer()
    }
}

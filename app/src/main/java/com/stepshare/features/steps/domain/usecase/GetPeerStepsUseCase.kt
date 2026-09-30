package com.Consnow.features.steps.domain.usecase

import com.Consnow.core.common.Result
import com.Consnow.features.steps.data.repository.PeerStepsResult
import com.Consnow.features.steps.domain.StepsRepository

class GetPeerStepsUseCase(private val repository: StepsRepository) {
    suspend operator fun invoke(peerId: String): Result<PeerStepsResult> {
        return repository.getPeerSteps(peerId)
    }
}

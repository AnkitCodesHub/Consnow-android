package com.Consnow.features.steps.domain.model

import com.Consnow.core.database.entity.SyncStatus

data class StepsState(
    val todaySteps: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val sensorAvailable: Boolean = true,
    val permissionGranted: Boolean = false
)

data class PeerStepsState(
    val isLoading: Boolean = false,
    val peerName: String = "",
    val stepCount: Int = 0,
    val error: String? = null
)

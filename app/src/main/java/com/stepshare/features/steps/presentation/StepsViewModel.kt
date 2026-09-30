package com.Consnow.features.steps.presentation

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.core.datastore.TokenManager
import com.Consnow.features.steps.data.SyncWorker
import com.Consnow.features.steps.data.sensor.StepCounterService
import com.Consnow.features.steps.domain.model.StepsState
import com.Consnow.features.steps.domain.usecase.ObserveTodayStepsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StepsViewModel(
    private val application: Application,
    private val observeTodayStepsUseCase: ObserveTodayStepsUseCase,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _state = MutableStateFlow(StepsState())
    val state = _state.asStateFlow()

    init {
        checkSensor()
        observeSteps()
    }

    private fun checkSensor() {
        val mgr = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        _state.update { it.copy(sensorAvailable = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) }
    }

    private fun observeSteps() {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            observeTodayStepsUseCase(userId).collect { record ->
                _state.update {
                    it.copy(
                        todaySteps = record?.stepCount ?: 0,
                        syncStatus = record?.syncStatus ?: SyncStatus.PENDING
                    )
                }
            }
        }
    }

    fun onPermissionGranted() {
        _state.update { it.copy(permissionGranted = true) }
        StepCounterService.start(application)
        SyncWorker.enqueuePeriodicSync(application)
    }

    fun onPermissionDenied() {
        _state.update { it.copy(permissionGranted = false) }
    }

    fun triggerSync() { SyncWorker.syncNow(application) }

    class Factory(
        private val application: Application,
        private val observeTodayStepsUseCase: ObserveTodayStepsUseCase,
        private val tokenManager: TokenManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StepsViewModel(application, observeTodayStepsUseCase, tokenManager) as T
    }
}

//Android UI
//   ↓
//StepsViewModel
//   ↓
//Room database / Step Service / Sync Worker
//   ↓
//Backend
//edited:02/10/2026 by Ankit
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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
private var lastObservedCount = 0//var to rem prev step count
    private var activityJob: Job? = null //steps detected start  10s timer
    private fun observeSteps() { //watch the changes in todays steps data
        viewModelScope.launch { //allow viewmodel to keep observing data without blocking ui
            val userId = tokenManager.getUserId() ?: return@launch //gets currently logged in user id
            observeTodayStepsUseCase(userId).collect { record ->
                val newCount = record?.stepCount ?: 0
                //observe a stream of step data
//Database changes
//      ↓
//record received
//      ↓
//run this code again
                val walking = newCount > lastObservedCount
                _state.update { //update viewmodel with new data
                    it.copy( //creates a new copy of the current state
                        todaySteps = newCount, //set todays count to new count
                        syncStatus = record?.syncStatus ?: SyncStatus.PENDING, //get the syncstatus from the record
                        isActive = walking, //compare curr step cnt to prev step cnt

                    )
                }
                lastObservedCount = newCount //store cur step cnt as prev step cnt for the next check

//                Steps increase → isActive=true, ripple ON, start 10s timer
//                More steps     → cancel old timer, start new 10s timer, ripple stays ON
//                No steps 10s   → timer fires → isActive=false → ripple OFF
                if (walking) {
                    activityJob?.cancel()
                    activityJob = viewModelScope.launch {
                        delay(10_000)
                        _state.update { it.copy(isActive = false) }//withoutthis there is no mech to turn of ripple
                    }
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

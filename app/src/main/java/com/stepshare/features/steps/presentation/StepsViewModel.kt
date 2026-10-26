//Android UI
//   ↓
//StepsViewModel
//   ↓
//Room database / Step Service / Sync Worker
//   ↓
//Backend
//edited: 02/10/2026 by Ankit
//updated: 14/10/2026 — added own lock status tracking
package com.Consnow.features.steps.presentation

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.Consnow.ConsnowApp
import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.core.datastore.TokenManager
import com.Consnow.features.steps.data.SyncWorker
import com.Consnow.features.steps.data.sensor.StepCounterService
import com.Consnow.features.steps.domain.model.StepsState
import com.Consnow.features.steps.domain.usecase.ObserveTodayStepsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant


class StepsViewModel(
    private val application: Application,
    private val observeTodayStepsUseCase: ObserveTodayStepsUseCase,
    private val tokenManager: TokenManager,
) : ViewModel() {

    // ── step + sensor state (existing) ───────────────────────────────────────
    private val _state = MutableStateFlow(StepsState())
    val state = _state.asStateFlow()

    // ── own lock status ───────────────────────────────────────────────────────
    // true by default — assume locked until we hear otherwise
    private val _ownIsLocked = MutableStateFlow(true)
    val ownIsLocked: StateFlow<Boolean> = _ownIsLocked.asStateFlow()

    // 0L = no unlock recorded yet
    private val _ownLastUnlockedAtMs = MutableStateFlow(0L)
    val ownLastUnlockedAtMs: StateFlow<Long> = _ownLastUnlockedAtMs.asStateFlow()

    init {
        checkSensor()
        observeSteps()
        observeLastActiveAt()
        fetchOwnLockStatus()     // pull last known state from backend on start
        observeOwnLockEvents()   // listen for live lock/unlock events from StepCounterService
    }

    // ── sensor check (existing) ───────────────────────────────────────────────
    private fun checkSensor() {
        val mgr = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        _state.update {
            it.copy(sensorAvailable = mgr.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null)
        }
    }

    // ── step observation (existing) ───────────────────────────────────────────
    private var lastObservedCount = 0   // remembers previous step count for delta check
    private var activityJob: Job? = null // timer that flips isActive → false after 10s

    private fun observeSteps() {
        viewModelScope.launch {
            val userId = tokenManager.getUserId() ?: return@launch
            observeTodayStepsUseCase(userId).collect { record ->
                val newCount = record?.stepCount ?: 0

                // walking = steps increased since last emission
                val walking = newCount > lastObservedCount

                _state.update {
                    it.copy(
                        todaySteps = newCount,
                        syncStatus = record?.syncStatus ?: SyncStatus.PENDING,
                        isActive = walking,
                    )
                }
                lastObservedCount = newCount

                // Steps increase   → isActive = true, cancel old timer, start 10s timer
                // No steps for 10s → timer fires → isActive = false → ripple OFF
                if (walking) {
                    activityJob?.cancel()
                    activityJob = viewModelScope.launch {
                        delay(10_000)
                        _state.update { it.copy(isActive = false) }
                    }
                }
            }
        }
    }

    // ── last active polling (existing) ────────────────────────────────────────
    // Polls the server every 15s and falls back to SharedPrefs cache across midnight
    private fun observeLastActiveAt() {
        viewModelScope.launch {
            val prefs = application.getSharedPreferences("consnow_prefs", Context.MODE_PRIVATE)
            while (true) {
                try {
                    val app = application as ConsnowApp
                    val today = java.time.LocalDate.now().toString()
                    val response = app.appContainer.stepsRepository.getMySteps(today)
                    if (response != null) {
                        val lastActiveAt = response.lastActiveAt
                        if (lastActiveAt != null) {
                            // Cache it — survives midnight rollover
                            prefs.edit().putString("last_active_at", lastActiveAt).apply()
                            _state.update { it.copy(lastActiveAt = lastActiveAt) }
                        } else {
                            // New day, no record yet — fall back to cached value
                            val cached = prefs.getString("last_active_at", null)
                            _state.update { it.copy(lastActiveAt = cached) }
                        }
                    }
                } catch (_: Exception) {
                }
                delay(15_000)
            }
        }
    }

    // ── own lock status — REST fetch on startup ───────────────────────────────
    // Gets the last known lock state from backend so the widget shows correctly
    // immediately when the screen opens, before any live event arrives
    private fun fetchOwnLockStatus() {
        viewModelScope.launch {
            try {
                val app = application as ConsnowApp
                val response = app.appContainer.apiService.getMyActivity()
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        _ownIsLocked.value = body.isLocked
                        // NEW
                        body.unlockedAt?.let { iso -> //edited on 21 oct
                            val ms = Instant.parse(iso).toEpochMilli()
                            if (ms > _ownLastUnlockedAtMs.value) _ownLastUnlockedAtMs.value = ms
                        }
                    }
                }
            } catch (_: Exception) {
                // Network unavailable — widget stays at default (locked, no time)
            }
        }
    }

    // ── own lock status — live EventBus listener ──────────────────────────────
    // StepCounterService emits OwnLockStatusEvent on every SCREEN_OFF / USER_PRESENT
    // This collector keeps the widget in sync with the phone's actual lock state
    private fun observeOwnLockEvents() {
        viewModelScope.launch {
            ActivityEventBus.ownLockStatus.collect { event ->
                // NEW
                _ownIsLocked.value = event.isLocked //edited on 21 oct
                if (event.timestamp > _ownLastUnlockedAtMs.value) _ownLastUnlockedAtMs.value =
                    event.timestamp
            }
        }
    }

    // ── permission + service control (existing) ───────────────────────────────
    fun onPermissionGranted() {
        _state.update { it.copy(permissionGranted = true) }
        StepCounterService.start(application)
        SyncWorker.enqueuePeriodicSync(application)
    }

    fun onPermissionDenied() {
        _state.update { it.copy(permissionGranted = false) }
    }

    fun triggerSync() {
        SyncWorker.syncNow(application)
    }
// added on 26/10/2026
    // ── account deletion ──────────────────────────────────────────────────────
    fun deleteAccount(onSuccess: () -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            try {
                val app = application as ConsnowApp
                val response = app.appContainer.apiService.deleteAccount()
                if (response.isSuccessful) {
                    // Clear local token and data
                    tokenManager.clearSession()
                    app.appContainer.database.clearAllTables()
                    onSuccess()
                } else {
                    onError()
                }
            } catch (e: Exception) {
                onError()
            }
        }
    }

    // ── factory ───────────────────────────────────────────────────────────────
    class Factory(
        private val application: Application,
        private val observeTodayStepsUseCase: ObserveTodayStepsUseCase,
        private val tokenManager: TokenManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StepsViewModel(application, observeTodayStepsUseCase, tokenManager) as T
    }
}
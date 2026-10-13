//edited: 04/10/2026 by Ankit
//updated: 14/10/2026 — added lock/unlock reporting
package com.Consnow.features.steps.data.sensor

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.Consnow.BuildConfig
import com.Consnow.ConsnowApp
import com.Consnow.MainActivity
import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.common.OwnLockStatusEvent
import com.Consnow.core.network.SocketManager
import com.Consnow.core.network.api.ApiService
import com.Consnow.features.steps.data.sensor.PhoneUnlockReceiver
import kotlinx.coroutines.*

class StepCounterService : Service(), SensorEventListener {

    private var socketManager: SocketManager? = null
    private var unlockReceiver: PhoneUnlockReceiver? = null
    private lateinit var apiService: ApiService

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var initialSensorValue: Float? = null
    private var lastSavedCount: Int = 0

    private var lastStepAt: Long = 0L
    private var isCurrentlyActive: Boolean = false
    private var activityCheckJob: Job? = null
    private var syncDebounceJob: Job? = null
    private var syncJob: Job? = null
    private var lastSyncTime = 0L

    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "step_sensor_prefs"
        private const val KEY_INITIAL_VALUE = "initial_sensor_value"
        private const val KEY_DATE = "sensor_date"
        private const val KEY_ACCUMULATED = "accumulated_steps"
        private const val KEY_LAST_STEP_AT = "last_step_at"
        const val SYNC_INTERVAL_MS = 1_000L
        const val SYNC_DEBOUNCE_MS = 1_000L
        // 60s of no steps = inactive
        private const val ACTIVITY_TIMEOUT_MS = 60 * 1000L
        // check activity status every 15s
        private const val ACTIVITY_CHECK_INTERVAL_MS = 15_000L

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepCounterService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        apiService = (application as ConsnowApp).appContainer.apiService
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        restoreState()
        startPeriodicSync()
        startActivityCheck()

        // Connect socket with JWT token
        val tokenManager = (application as ConsnowApp).appContainer.tokenManager
        serviceScope.launch {
            val token = tokenManager.getToken() ?: ""
            if (token.isNotEmpty()) {
                socketManager = SocketManager(BuildConfig.API_BASE_URL, token)
                socketManager?.connect()
            }
        }

        // Emit current lock state immediately on service start
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        serviceScope.launch {
            ActivityEventBus.emitOwnLockStatus(
                OwnLockStatusEvent(
                    isLocked = km.isKeyguardLocked,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }

        // Register for both unlock and screen-off broadcasts
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT) // phone unlocked
            addAction(Intent.ACTION_SCREEN_OFF)   // screen off = locked
        }
        unlockReceiver = PhoneUnlockReceiver(
            onUnlock = { serviceScope.launch { reportUnlock() } },
            onLock = { serviceScope.launch { reportLock() } },
        )
        registerReceiver(unlockReceiver, filter)
    }

    // Reports unlock to backend + emits own status to HomeScreen immediately
    private suspend fun reportUnlock() {
        try {
            apiService.reportUnlock()
            ActivityEventBus.emitOwnLockStatus(
                OwnLockStatusEvent(isLocked = false, timestamp = System.currentTimeMillis())
            )
            Log.d("StepCounterService", "Unlock reported")
        } catch (e: Exception) {
            Log.e("StepCounterService", "Failed to report unlock: ${e.message}")
        }
    }

    // Reports lock to backend + emits own status to HomeScreen immediately
    private suspend fun reportLock() {
        try {
            apiService.reportLock()
            ActivityEventBus.emitOwnLockStatus(
                OwnLockStatusEvent(isLocked = true, timestamp = System.currentTimeMillis())
            )
            Log.d("StepCounterService", "Lock reported")
        } catch (e: Exception) {
            Log.e("StepCounterService", "Failed to report lock: ${e.message}")
        }
    }

    // Polls backend every 15s as a fallback sync
    private fun startPeriodicSync() {
        serviceScope.launch {
            while (true) {
                delay(15_000)
                try {
                    val app = application as ConsnowApp
                    val result = app.appContainer.stepsRepository.syncPendingToServer(isCurrentlyActive)
                    Log.d("StepSync", "Sync completed: $result")
                } catch (e: Exception) {
                    Log.e("StepSync", "Sync failed: ${e.message}")
                }
            }
        }
    }

    // Flips isCurrentlyActive → false after ACTIVITY_TIMEOUT_MS of no steps
    private fun startActivityCheck() {
        activityCheckJob?.cancel()
        activityCheckJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val wasActive = isCurrentlyActive
                isCurrentlyActive = lastStepAt > 0 && (now - lastStepAt) < ACTIVITY_TIMEOUT_MS
                // Flip: was walking → now idle → force sync
                if (wasActive && !isCurrentlyActive) startSync()
                delay(ACTIVITY_CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        lastStepAt = System.currentTimeMillis()
        isCurrentlyActive = true

        val totalSinceReboot = event.values[0]
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_DATE, null)
        prefs.edit().putLong(KEY_LAST_STEP_AT, lastStepAt).apply()

        if (savedDate != today) {
            // New day — reset baseline
            initialSensorValue = totalSinceReboot
            prefs.edit()
                .putFloat(KEY_INITIAL_VALUE, totalSinceReboot)
                .putString(KEY_DATE, today)
                .putInt(KEY_ACCUMULATED, 0)
                .apply()
        }

        if (initialSensorValue == null) {
            initialSensorValue = totalSinceReboot
            prefs.edit().putFloat(KEY_INITIAL_VALUE, totalSinceReboot).apply()
        }

        val todaySteps = (totalSinceReboot - (initialSensorValue ?: totalSinceReboot)).toInt()
            .coerceAtLeast(0) + prefs.getInt(KEY_ACCUMULATED, 0)

        if (todaySteps != lastSavedCount) {
            lastSavedCount = todaySteps
            persistSteps(todaySteps)
            updateNotification(todaySteps)
        }
    }

    // Saves steps locally then schedules a network sync
    private fun persistSteps(count: Int) {
        serviceScope.launch {
            try {
                val app = application as ConsnowApp
                val userId = app.appContainer.tokenManager.getUserId() ?: return@launch
                app.appContainer.stepsRepository.updateLocalSteps(userId, count)
                scheduleSync()
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {}
        }
    }

    // Immediate sync if interval passed, debounced sync otherwise
    private fun scheduleSync() {
        val now = System.currentTimeMillis()
        if (now - lastSyncTime >= SYNC_INTERVAL_MS) {
            syncDebounceJob?.cancel()
            syncDebounceJob = null
            lastSyncTime = now
            startSync()
        } else {
            syncDebounceJob?.cancel()
            syncDebounceJob = serviceScope.launch {
                delay(SYNC_DEBOUNCE_MS)
                lastSyncTime = System.currentTimeMillis()
                startSync()
            }
        }
    }

    // Performs the actual network sync — skips if one is already running
    private fun startSync() {
        if (syncJob?.isActive == true) return
        syncJob = serviceScope.launch {
            try {
                val app = application as ConsnowApp
                app.appContainer.stepsRepository.syncPendingToServer(isCurrentlyActive)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {}
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        val today = java.time.LocalDate.now().toString()
        if (savedDate == today) {
            initialSensorValue = prefs.getFloat(KEY_INITIAL_VALUE, 0f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        unlockReceiver?.let { unregisterReceiver(it) }
        unlockReceiver = null
        socketManager?.disconnect()
        socketManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Step Tracking", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Counting your steps" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Consnow")
            .setContentText("$steps steps today")
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(steps: Int) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(steps))
    }
}
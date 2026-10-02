package com.Consnow.features.steps.data.sensor

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.Consnow.MainActivity
import com.Consnow.ConsnowApp
import kotlinx.coroutines.*
import kotlinx.coroutines.delay


class StepCounterService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // TYPE_STEP_COUNTER gives total steps since last reboot.
    // We track the initial reading and compute today's delta.
    private var initialSensorValue: Float? = null
    private var lastSavedCount: Int = 0

    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "step_sensor_prefs"
        private const val KEY_INITIAL_VALUE = "initial_sensor_value"
        private const val KEY_DATE = "sensor_date"
        private const val KEY_ACCUMULATED = "accumulated_steps"
        //makes user idle when no movement from 3 hours
        private const val ACTIVITY_TIMEOUT_MS = 3 * 60 * 1000L // 3mins
//  checks every 30 seconds and update the idle time
//  Every 30 seconds:
//  Has it been 3+ minutes since lastStepAt?
//    No  → user is still walking, keep isCurrentlyActive = true
//    Yes → user stopped, set isCurrentlyActive = false

        private const val ACTIVITY_CHECK_INTERVAL_MS = 30_000L   // 30 seconds
    }
    private var lastStepAt: Long = 0L
    private var isCurrentlyActive: Boolean = false
    private var activityCheckJob: Job? = null


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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        restoreState()
        startPeriodicSync() //calling the function to start periodic sync
    }

    private fun startPeriodicSync() {
        serviceScope.launch {
            while (true) {
                delay(120_000) // wait for 2 minutes
                try {
                    val app = application as ConsnowApp //get the app instance
                    val result = app.appContainer.stepsRepository.syncPendingToServer() //acess step repo and find all peniding recordes in room
                    android.util.Log.d("StepSync", "Sync completed: $result")  //sync each and retrun the result
                } catch (e: Exception) { // if not synced due to any reason skip and retry aftr 2 mins
                    android.util.Log.e("StepSync", "Sync failed: ${e.message}")
                }
            }
        }
    }
private fun startActivityCheck() {
    activityCheckJob?.cancel()
    activityCheckJob = serviceScope.launch {
        while (isActive) {
            val now = System.currentTimeMillis()
            isCurrentlyActive = lastStepAt > 0 && (now - lastStepAt) < ACTIVITY_TIMEOUT_MS
            delay(ACTIVITY_CHECK_INTERVAL_MS)
        }
    }
}

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
//        saves teh exact moment last step taken
        lastStepAt = System.currentTimeMillis()
//        flags if user is currently walking
        isCurrentlyActive = true
        val totalSinceReboot = event.values[0]
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString(KEY_DATE, null)

        if (savedDate != today) {
            // New day: reset baseline
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

    private fun persistSteps(count: Int) {
        serviceScope.launch {
            try {
                val app = application as ConsnowApp
                val userId = app.appContainer.tokenManager.getUserId() ?: return@launch
                app.appContainer.stepsRepository.updateLocalSteps(userId, count)
            } catch (_: Exception) { }
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Step Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Counting your steps" }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
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
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIFICATION_ID, buildNotification(steps))
    }
}

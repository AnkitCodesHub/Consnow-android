//edited: 04/10/2026 by Ankit
//updated: 14/10/2026 — added lock/unlock reporting
//updated: 15/10/2026 — added offline queue, heartbeat, LockSyncWorker
//updated: 20/10/2026 — simplified lock/unlock: dropped Room queue, timestamp now passed from receiver
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
import com.Consnow.core.common.TrustedClock
import com.Consnow.core.network.SocketManager
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.LockEventRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged

class StepCounterService : Service(), SensorEventListener {

    private var socketManager: SocketManager? = null
    private var unlockReceiver: PhoneUnlockReceiver? = null
    private lateinit var apiService: ApiService

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Today's step bookkeeping (see StepCounterLogic). Only read and changed on the main
    // thread: sensor callbacks arrive there, and coroutines switch to Main before touching it.
    private var counter = CounterState()
    private var lastSavedCount: Int = 0
    private val mergeRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    private var lastStepAt: Long = 0L
    // Same moment on the trusted (server-corrected) clock: this is what gets stored and sent
    private var lastStepTrustedAt: Long = 0L
    private var isCurrentlyActive: Boolean = false
    private var activityCheckJob: Job? = null
    private var syncDebounceJob: Job? = null
    private var syncJob: Job? = null
    private var lastSyncTime = 0L

    // Heartbeat job — runs every 60s while phone is unlocked.
    // Keeps lastHeartbeatAt fresh on server so peers know user is online.
    // Cancelled when phone locks, started when phone unlocks.
    private var heartbeatJob: Job? = null

    // Tracks the last known lock state to guard against duplicate broadcasts.
    // ACTION_SCREEN_OFF can fire multiple times; this ensures we only report
    // a real transition (unlocked → locked or locked → unlocked).
    private var lastKnownLocked: Boolean? = null

    companion object {
        const val CHANNEL_ID = "step_counter_channel"
        const val NOTIFICATION_ID = 1
        private const val PREFS_NAME = "step_sensor_prefs"
        private const val KEY_INITIAL_VALUE = "initial_sensor_value"
        private const val KEY_DATE = "sensor_date"
        private const val KEY_ACCUMULATED = "accumulated_steps"
        private const val KEY_LAST_STEP_AT = "last_step_at"
        // Last raw sensor value seen. If a new reading is lower, the counter was reset (reboot).
        private const val KEY_LAST_TOTAL = "last_sensor_total"
        // Last computed step count for today, carried forward across a reboot.
        private const val KEY_LAST_COUNT = "last_today_count"
        // userId the saved count belongs to
        private const val KEY_OWNER = "count_owner"
        // Counting started with no saved data (reinstall / cleared data): server count is added
        private const val KEY_FRESH = "fresh_since_wipe"
        // Set at login; cleared once today's server count has been merged in
        private const val KEY_MERGE_PENDING = "server_merge_pending"
        const val SYNC_INTERVAL_MS = 1_000L
        const val SYNC_DEBOUNCE_MS = 1_000L
        // 15s of no steps = inactive (was 60s; reduced so friends see
        // "stopped walking" within ~20s instead of ~2 minutes)
        const val ACTIVITY_TIMEOUT_MS = 15_000L
        // check activity status every 5s (was 15s; reduced for faster transition)
        const val ACTIVITY_CHECK_INTERVAL_MS = 5_000L

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

        /**
         * Called on login/register, before the session is saved. The service then merges
         * today's server count once (see runServerMergeIfPending).
         */
        fun markServerMergePending(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MERGE_PENDING, true).apply()
        }

        /**
         * True until the login merge is done. Step syncs wait while this is true so the
         * server can't take in steps that are about to be added to its own count.
         */
        fun isServerMergePending(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_MERGE_PENDING, false)

        private fun clearServerMergePending(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_MERGE_PENDING, false).apply()
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
        // Date check + removal of future-dated rows right away, not after the first 15s tick
        serviceScope.launch {
            try { checkAndResetForNewDay() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        // Brings in today's server count once after a login (no-op otherwise)
        runServerMergeIfPending()

        // Connect WebSocket with the CURRENT JWT and reconnect whenever it changes.
        // Before, the socket kept the token it started with: once that expired the
        // server rejected it and live updates stopped until the app was reinstalled.
        val tokenManager = (application as ConsnowApp).appContainer.tokenManager
        serviceScope.launch {
            tokenManager.tokenFlow.distinctUntilChanged().collect { token ->
                socketManager?.disconnect()
                socketManager = null
                if (!token.isNullOrEmpty()) {
                    socketManager = SocketManager(BuildConfig.API_BASE_URL, token)
                    socketManager?.connect()
                    // A login while the service was already running
                    runServerMergeIfPending()
                }
            }
        }

        // Snapshot the current lock state when the service starts.
        // Emits timestamp=0 so the server and peers never treat this as a
        // real transition — it is a state report only, not a new unlock event.
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val initiallyLocked = km.isKeyguardLocked
        lastKnownLocked = initiallyLocked
        serviceScope.launch {
            ActivityEventBus.emitOwnLockStatus(
                OwnLockStatusEvent(isLocked = initiallyLocked, timestamp = 0L)
            )
        }
        // Heartbeat runs whether locked or unlocked, so friends see us as
        // online whenever the phone has internet (lock state is reported separately)
        startHeartbeat()

        // Register BroadcastReceiver for lock/unlock transitions.
        // ACTION_USER_PRESENT fires when the user dismisses the lock screen.
        // ACTION_SCREEN_OFF fires when the screen turns off (= phone locked).
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        unlockReceiver = PhoneUnlockReceiver(
            onUnlock = {
                // Guard: ignore if we already think the phone is unlocked
                if (lastKnownLocked != false) {
                    lastKnownLocked = false
                    val at = System.currentTimeMillis()
                    serviceScope.launch { reportUnlock(at) }
                }
            },
            onLock = {
                // Guard: ignore SCREEN_OFF if phone was never seen as unlocked
                if (lastKnownLocked != true) {
                    lastKnownLocked = true
                    val at = System.currentTimeMillis()
                    serviceScope.launch { reportLock(at) }
                }
            },
        )
        registerReceiver(unlockReceiver, filter)
    }

    /**
     * Called when the user unlocks their phone.
     *
     * [at] is the epoch-millis timestamp captured in the BroadcastReceiver
     * callback — this is the moment the transition happened, not when the
     * coroutine runs. Sending it to the server allows the guarded upsert
     * (WHERE unlockedAt <= EXCLUDED.unlockedAt) to reject stale events.
     *
     * Flow:
     * 1. Start heartbeat so server knows user is online
     * 2. Emit local state so HomeScreen updates instantly
     * 3. POST to server with the exact transition timestamp
     */
    private suspend fun reportUnlock(at: Long) {
        startHeartbeat()
        ActivityEventBus.emitOwnLockStatus(OwnLockStatusEvent(isLocked = false, timestamp = at))
        try {
            apiService.reportUnlock(LockEventRequest(occurredAt = at))
        } catch (e: Exception) {
            Log.e("StepCounterService", "Failed to report unlock: ${e.message}")
        }
    }

    /**
     * Called when the user locks their phone (screen off).
     *
     * [at] is the epoch-millis timestamp captured in the BroadcastReceiver
     * callback — same rationale as reportUnlock.
     *
     * Flow:
     * 1. Stop heartbeat — no need to ping while locked
     * 2. Emit local state so HomeScreen updates instantly
     * 3. POST to server with the exact transition timestamp
     */
    private suspend fun reportLock(at: Long) {
        // Heartbeat keeps running while locked so we still show as online
        ActivityEventBus.emitOwnLockStatus(OwnLockStatusEvent(isLocked = true, timestamp = at))
        try {
            apiService.reportLock(LockEventRequest(occurredAt = at))
        } catch (e: Exception) {
            Log.e("StepCounterService", "Failed to report lock: ${e.message}")
        }
    }

    /**
     * Sends a heartbeat ping to the server every 60s while phone is unlocked.
     * Server uses lastHeartbeatAt to determine online state:
     *   - Fresh heartbeat (< 90s ago) → peer sees user as online + unlocked
     *   - Stale heartbeat (> 90s ago) → peer sees user as offline
     * This handles the case where the user goes offline while unlocked.
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    apiService.sendHeartbeat()
                    Log.d("Heartbeat", "Sent")
                } catch (_: Exception) {}
                delay(60_000L)
            }
        }
    }

    // Stops the heartbeat loop — called when phone locks or service is destroyed
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    // Polls step sync every 15s as fallback when foreground service is killed by OxygenOS
    private fun startPeriodicSync() { //edited on 29/0ctober/2026 to prevent midnight freeze
        serviceScope.launch {
            while (true) {
                delay(15_000)
                try {
                    val app = application as ConsnowApp
                    // Reset baseline if day rolled over while phone was idle (no steps = no sensor event)
                    checkAndResetForNewDay()
                    // Login merge still waiting (e.g. no internet at login): try again
                    runServerMergeIfPending()
                    // Renews the JWT once a day while online, so active users never hit expiry
                    app.appContainer.authRepository.refreshTokenIfStale()
                    val result = app.appContainer.stepsRepository.syncPendingToServer(isCurrentlyActive)
                    Log.d("StepSync", "Sync completed: $result")
                } catch (e: Exception) {
                    Log.e("StepSync", "Sync failed: ${e.message}")
                }
            }
        }
    }

    // Date check while the phone is still (no steps = no sensor event). Also moves a count
    // saved under a future date (clock was set ahead) back to today and deletes future rows.
    private suspend fun checkAndResetForNewDay() {
        val today = TrustedClock.today()
        val changed = withContext(Dispatchers.Main) {
            val before = counter.date
            counter = StepCounterLogic.onDayCheck(counter, today)
            if (counter.date == before) return@withContext null
            saveState()
            lastSavedCount = counter.lastCount
            updateNotification(counter.lastCount)
            Log.d("StepSync", "Day check: $before -> ${counter.date}, count=${counter.lastCount}")
            counter
        }
        (application as ConsnowApp).appContainer.stepsRepository.deleteFutureRecords(today)
        if (changed != null && changed.lastCount > 0) persistSteps(changed)
    }

    // Flips isCurrentlyActive → false after ACTIVITY_TIMEOUT_MS of no steps.
    // Forces a sync when user goes from walking → idle so server gets the final count.
    private fun startActivityCheck() {
        activityCheckJob?.cancel()
        activityCheckJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val wasActive = isCurrentlyActive
                isCurrentlyActive = lastStepAt > 0 && (now - lastStepAt) < ACTIVITY_TIMEOUT_MS
                if (wasActive && !isCurrentlyActive) startSync()
                delay(ACTIVITY_CHECK_INTERVAL_MS)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor?.type != Sensor.TYPE_STEP_COUNTER) return

        lastStepAt = System.currentTimeMillis()
        lastStepTrustedAt = TrustedClock.nowMs()
        isCurrentlyActive = true

        // New day, phone reboot and future-date repair are all handled in StepCounterLogic
        counter = StepCounterLogic.onReading(counter, event.values[0], TrustedClock.today())
        saveState()

        if (counter.lastCount != lastSavedCount) {
            lastSavedCount = counter.lastCount
            persistSteps(counter)
            updateNotification(counter.lastCount)
        }
    }

    // Saves steps to Room then schedules a network sync.
    // Only for the account that owns the count: right after a login the count may still
    // belong to the previous account until runServerMergeIfPending claims it.
    private fun persistSteps(snapshot: CounterState) {
        val owner = snapshot.owner ?: return
        val date = snapshot.date ?: return
        val count = snapshot.lastCount
        val stepAt = lastStepTrustedAt.takeIf { it > 0 } ?: TrustedClock.nowMs()
        serviceScope.launch {
            try {
                val app = application as ConsnowApp
                if (app.appContainer.tokenManager.getUserId() != owner) return@launch
                app.appContainer.stepsRepository.updateLocalSteps(owner, count, date, stepAt)
                scheduleSync()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    // Immediate sync if interval has passed, debounced sync otherwise.
    // Prevents hammering the server on every step event.
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

    // Performs the actual network sync — skips if one is already in flight
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

    // Restores the counter from SharedPreferences after a service restart
    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        counter = CounterState(
            owner = prefs.getString(KEY_OWNER, null),
            date = savedDate,
            initial = if (prefs.contains(KEY_INITIAL_VALUE)) prefs.getFloat(KEY_INITIAL_VALUE, 0f) else null,
            accumulated = prefs.getInt(KEY_ACCUMULATED, 0),
            lastTotal = prefs.getFloat(KEY_LAST_TOTAL, -1f),
            lastCount = prefs.getInt(KEY_LAST_COUNT, 0),
            // No saved day at all = fresh install or cleared data. A phone updated from an
            // older app version has a saved day but no flag yet, so it is not fresh.
            freshSinceWipe = if (prefs.contains(KEY_FRESH)) prefs.getBoolean(KEY_FRESH, false)
                             else savedDate == null,
        )
    }

    private fun saveState() {
        val c = counter
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_OWNER, c.owner)           // null removes the key
            putString(KEY_DATE, c.date)
            if (c.initial != null) putFloat(KEY_INITIAL_VALUE, c.initial) else remove(KEY_INITIAL_VALUE)
            putInt(KEY_ACCUMULATED, c.accumulated)
            putFloat(KEY_LAST_TOTAL, c.lastTotal)
            putInt(KEY_LAST_COUNT, c.lastCount)
            putBoolean(KEY_FRESH, c.freshSinceWipe)
            if (lastStepTrustedAt > 0) putLong(KEY_LAST_STEP_AT, lastStepTrustedAt)
        }.apply()
    }

    /**
     * Login merge: brings in today's count from the server once after each login.
     *  - phone data was wiped (reinstall / clear data) -> server count + steps counted since
     *  - phone data intact (logged out and back in)    -> whichever is higher
     * Never lowers the count and never counts the same steps twice. Step syncs wait until
     * this is done (isServerMergePending), and it retries every 15s while offline.
     */
    private fun runServerMergeIfPending() {
        if (!isServerMergePending(this)) return
        if (!mergeRunning.compareAndSet(false, true)) return
        serviceScope.launch {
            try {
                val app = application as ConsnowApp
                val repo = app.appContainer.stepsRepository
                val userId = app.appContainer.tokenManager.getUserId() ?: return@launch
                withContext(Dispatchers.Main) {
                    counter = StepCounterLogic.claimFor(counter, userId)
                    saveState()
                }
                val date = TrustedClock.today()
                val serverCount = repo.fetchServerStepCount(date) ?: return@launch // offline: retried
                val merged = withContext(Dispatchers.Main) {
                    if (counter.owner != userId) return@withContext null // account changed meanwhile
                    val m = StepCounterLogic.mergeServer(counter, serverCount, date, TrustedClock.today())
                        ?: return@withContext null // midnight passed during the request: retried
                    counter = m
                    saveState()
                    lastSavedCount = m.lastCount
                    updateNotification(m.lastCount)
                    m
                } ?: return@launch
                val stepAt = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getLong(KEY_LAST_STEP_AT, 0L).takeIf { it > 0 } ?: TrustedClock.nowMs()
                repo.mergeLocalSteps(userId, date, merged.lastCount, merged.lastCount > serverCount, stepAt)
                clearServerMergePending(this@StepCounterService)
                Log.d("StepSync", "Login merge done: server=$serverCount, today=${merged.lastCount}")
                startSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StepSync", "Login merge failed: ${e.message}")
            } finally {
                mergeRunning.set(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        serviceScope.cancel()
        stopHeartbeat()
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
package com.Consnow.features.steps.data

import android.content.Context
import androidx.work.*
import com.Consnow.ConsnowApp
import com.Consnow.core.database.LockEventType
import com.Consnow.core.network.dto.LockEventRequest
import java.util.concurrent.TimeUnit

class LockSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ConsnowApp
        val dao = app.appContainer.database.pendingLockEventDao()
        val api = app.appContainer.apiService

        val pending = dao.getUnsynced()
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (event in pending) {
            try {
                val body = LockEventRequest(occurredAt = event.occurredAt)
                val response = when (event.eventType) {
                    LockEventType.UNLOCK -> api.reportUnlock(body)
                    LockEventType.LOCK   -> api.reportLock(body)
                }
                if (response.isSuccessful) {
                    dao.markSynced(event.id)
                } else {
                    anyFailed = true
                }
            } catch (e: Exception) {
                anyFailed = true
            }
        }

        dao.deleteAllSynced()
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "lock_sync_worker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<LockSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
        }
    }
}

package com.Consnow.features.steps.data.repository

import com.Consnow.core.database.dao.StepRecordDao
import com.Consnow.core.database.entity.StepRecordEntity
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.SyncStepsRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import java.time.LocalDate

/**
 * Regression: friend stopped walking but the server kept saying "Walking" for ~2 minutes,
 * because syncPendingToServer(isActive=false) returned early when nothing was PENDING.
 */
class StopWalkingSyncTest {

    private val today = LocalDate.now().toString()
    private var pending: List<StepRecordEntity> = emptyList()
    private val record = StepRecordEntity(id = 1, userId = "me", stepCount = 100, date = today)
    private val sent = mutableListOf<SyncStepsRequest>()

    private inline fun <reified T> fake(noinline h: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { p, m, a ->
            when (m.name) {
                "toString" -> "Fake"
                "hashCode" -> System.identityHashCode(p)
                "equals" -> p === a?.get(0)
                else -> h(m.name, a ?: emptyArray())
            }
        } as T

    private fun repo(): StepsRepositoryImpl {
        val dao = fake<StepRecordDao> { name, _ ->
            when (name) {
                "getRecordsByStatus" -> pending
                "getRecordByDate" -> record
                "markSyncedIfUnchanged" -> { pending = emptyList(); 1 }
                else -> throw AssertionError("Unexpected DAO call: $name")
            }
        }
        val api = fake<ApiService> { name, args ->
            if (name != "syncSteps") throw AssertionError("Unexpected API call: $name")
            sent += args[0] as SyncStepsRequest
            Response.success<Any>(Any())
        }
        return StepsRepositoryImpl(dao, api)
    }

    @Test fun `stopping after steps already synced still tells the server isActive false`() = runBlocking {
        val r = repo()
        pending = listOf(record.copy(syncStatus = SyncStatus.PENDING))
        r.syncPendingToServer(isActive = true)          // walking: step synced, nothing pending now
        assertEquals(true, sent.single().isActive)

        r.syncPendingToServer(isActive = false)         // stopped, nothing pending
        assertEquals(2, sent.size)
        assertFalse("server must be told walking stopped", sent.last().isActive)
        assertEquals("step count unchanged", 100, sent.last().stepCount)
    }

    @Test fun `repeated idle ticks send the stop update only once`() = runBlocking {
        val r = repo()
        pending = listOf(record)
        r.syncPendingToServer(true)
        r.syncPendingToServer(false)
        r.syncPendingToServer(false)
        r.syncPendingToServer(false)
        assertEquals("1 walking + 1 stop, no 15s spam", 2, sent.size)
    }

    @Test fun `never walked means no request when idle`() = runBlocking {
        val r = repo()
        r.syncPendingToServer(false)
        assertEquals(0, sent.size)
    }
}

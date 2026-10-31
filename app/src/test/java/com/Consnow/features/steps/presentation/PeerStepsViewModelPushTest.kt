package com.Consnow.features.steps.presentation

import com.Consnow.core.common.ActivityEventBus
import com.Consnow.core.common.PeerActivityEvent
import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.PeerActivityResponse
import com.Consnow.features.steps.data.repository.PeerStepsResult
import com.Consnow.features.steps.domain.StepsRepository
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

/** peer_activity push on the single-friend screen: walking starts/stops instantly. */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerStepsViewModelPushTest {

    private val scheduler = TestCoroutineScheduler()
    private var holdSteps = false
    private var heldSteps: Continuation<Any?>? = null
    private var serverWalking = false
    private var nextId = 0

    private inline fun <reified T> fake(noinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "Fake"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T

    private fun steps(active: Boolean) =
        Result.Success(PeerStepsResult("Peer", 100, "2026-11-01", active, "2026-11-01T09:00:00.000Z"))

    private fun newViewModel(peerId: String): PeerStepsViewModel {
        val repo = fake<StepsRepository> { name, args ->
            if (name != "getPeerSteps") throw AssertionError("Unexpected repo call: $name")
            if (holdSteps) {
                @Suppress("UNCHECKED_CAST")
                heldSteps = args.last() as Continuation<Any?>
                COROUTINE_SUSPENDED
            } else steps(serverWalking)
        }
        val api = fake<ApiService> { name, _ ->
            if (name != "getPeerActivity") throw AssertionError("Unexpected API call: $name")
            Response.success(PeerActivityResponse(userId = peerId, unlockedAt = null, isLocked = true, isOnline = true))
        }
        return PeerStepsViewModel(GetPeerStepsUseCase(repo), api, peerId)
    }

    private fun push(id: String, active: Boolean, lastActiveAt: String? = "2026-11-01T10:00:00.000Z") {
        runBlocking { ActivityEventBus.emit(PeerActivityEvent(id, active, lastActiveAt)) }
        scheduler.runCurrent()
    }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `push walking start updates the screen immediately`() {
        val id = "peer-start-${nextId++}"
        val vm = newViewModel(id); scheduler.runCurrent()
        assertFalse(vm.state.value.isActive)

        push(id, active = true)
        assertTrue(vm.state.value.isActive)
        assertEquals("step count is kept", 100, vm.state.value.stepCount)
    }

    @Test fun `push walking stop updates the screen and uses the real last-step time`() {
        val id = "peer-stop-${nextId++}"
        serverWalking = true
        val vm = newViewModel(id); scheduler.runCurrent()
        assertTrue(vm.state.value.isActive)

        push(id, active = false, lastActiveAt = "2026-11-01T10:05:00.000Z")
        assertFalse(vm.state.value.isActive)
        assertEquals("2026-11-01T10:05:00.000Z", vm.state.value.lastActiveAt)
    }

    @Test fun `push about another friend is ignored`() {
        val id = "peer-other-${nextId++}"
        val vm = newViewModel(id); scheduler.runCurrent()
        push("someone-else", active = true)
        assertFalse(vm.state.value.isActive)
    }

    @Test fun `push without a timestamp keeps the previous last-active time`() {
        val id = "peer-nots-${nextId++}"
        val vm = newViewModel(id); scheduler.runCurrent()
        val before = vm.state.value.lastActiveAt
        push(id, active = true, lastActiveAt = null)
        assertEquals(before, vm.state.value.lastActiveAt)
    }

    @Test fun `a slow steps fetch that started before the push cannot undo it`() {
        val id = "peer-race-${nextId++}"
        holdSteps = true
        val vm = newViewModel(id); scheduler.runCurrent()   // initial fetch in flight

        push(id, active = true)                              // push arrives first
        holdSteps = false
        heldSteps!!.resume(steps(active = false))            // old response lands late
        scheduler.runCurrent()

        assertTrue("stale response must not overwrite the newer push", vm.state.value.isActive)
    }
}

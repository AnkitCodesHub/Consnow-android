package com.Consnow.features.steps.presentation

import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.PeerActivityResponse
import com.Consnow.core.network.dto.PeerStepsResponse
import com.Consnow.features.steps.domain.StepsRepository
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import java.time.Instant

/**
 * Tests for the Home screen friends list status (test plan U8–U13).
 *
 * The fake ApiService is a java.lang.reflect.Proxy: it pretends to implement the whole
 * interface, and we only answer getPeerActivity. Any other call fails the test loudly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerStatusesViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    /** What the fake server answers for getPeerActivity. Change it between polls. */
    private var answer: (String) -> Response<PeerActivityResponse> = { ok(it, online = true, locked = false) }
    /** What the fake server answers for getPeerSteps. */
    private var stepsAnswer: (String) -> Response<PeerStepsResponse> = { okSteps(it) }
    private var calls = 0

    private inline fun <reified T> fake(noinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "Fake ${T::class.java.simpleName}"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T

    private fun newViewModel(): PeerStatusesViewModel {
        val api = fake<ApiService> { name, args ->
            when (name) {
                "getPeerActivity" -> {
                    calls++
                    answer(args[0] as String)
                }
                "getPeerSteps" -> {
                    stepsAnswer(args[0] as String)
                }
                else -> throw AssertionError("Unexpected API call: $name")
            }
        }
        val repo = fake<StepsRepository> { name, _ -> throw AssertionError("Unexpected repo call: $name") }
        return PeerStatusesViewModel(GetPeerStepsUseCase(repo), api)
    }

    private fun ok(id: String, online: Boolean, locked: Boolean, unlockedAt: String? = null) =
        Response.success(PeerActivityResponse(userId = id, unlockedAt = unlockedAt, isLocked = locked, isOnline = online))

    private fun okSteps(id: String, isActive: Boolean = false, stepCount: Int = 0) =
        Response.success(PeerStepsResponse(userId = id, userName = "Test", date = java.time.LocalDate.now().toString(), stepCount = stepCount, updatedAt = null, isActive = isActive))

    private fun runPollNow() = scheduler.runCurrent()
    private fun waitOnePoll() { scheduler.advanceTimeBy(20_000); scheduler.runCurrent() }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `U8 server says online and unlocked`() {
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertTrue(s.isOnline)
        assertFalse(s.isLocked)
        vm.track(emptyList())
    }

    @Test fun `U9 offline but unlocked is shown as locked`() {
        answer = { ok(it, online = false, locked = false) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertFalse(s.isOnline)
        assertTrue("offline must always show Locked", s.isLocked)
        vm.track(emptyList())
    }

    @Test fun `U10 network exception gives offline and locked`() {
        answer = { throw IOException("no internet") }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertFalse(s.isOnline)
        assertTrue(s.isLocked)
        vm.track(emptyList())
    }

    @Test fun `U10b 403 from server (friend removed) gives offline and locked, no crash`() {
        answer = { Response.error(403, "{}".toResponseBody(null)) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertFalse(s.isOnline)
        assertTrue(s.isLocked)
        vm.track(emptyList())
    }

    @Test fun `U11 older unlock time never replaces a newer one`() {
        val newer = "2026-10-29T10:05:00.000Z"
        val older = "2026-10-29T10:02:00.000Z"
        answer = { ok(it, online = true, locked = true, unlockedAt = newer) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertEquals(Instant.parse(newer).toEpochMilli(), vm.statuses.value.getValue("p1").lastUnlockedAtMs)

        answer = { ok(it, online = true, locked = true, unlockedAt = older) }
        waitOnePoll()
        assertEquals("time must never go back", Instant.parse(newer).toEpochMilli(), vm.statuses.value.getValue("p1").lastUnlockedAtMs)
        vm.track(emptyList())
    }

    @Test fun `U12 track twice with same ids runs only one polling loop`() {
        val vm = newViewModel()
        vm.track(listOf("p1", "p2")); runPollNow()
        vm.track(listOf("p1", "p2")); runPollNow()
        assertEquals("first poll: one call per friend", 2, calls)
        waitOnePoll()
        assertEquals("second poll: still one call per friend (two loops would give 6)", 4, calls)
        vm.track(emptyList())
    }

    @Test fun `U13 track with empty list stops polling`() {
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertEquals(1, calls)
        vm.track(emptyList())
        scheduler.advanceTimeBy(120_000); scheduler.runCurrent()
        assertEquals("no calls after polling stopped", 1, calls)
    }

    @Test fun `changing the friend list restarts polling for the new friends`() {
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        vm.track(listOf("p2")); runPollNow()
        assertTrue(vm.statuses.value.containsKey("p2"))
        waitOnePoll()
        assertEquals("p1 once, then p2 twice; old loop is gone", 3, calls)
        vm.track(emptyList())
    }

    // ── isUserActive (green/red dot) tests ──────────────────────────────

    @Test fun `green dot when online and unlocked and not walking`() {
        answer = { ok(it, online = true, locked = false) }
        stepsAnswer = { okSteps(it, isActive = false) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertTrue("unlocked phone = active user", s.isUserActive)
        vm.track(emptyList())
    }

    @Test fun `green dot when online and locked but walking`() {
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertTrue("walking while locked = active user", s.isUserActive)
        vm.track(emptyList())
    }

    @Test fun `red dot when online and locked and not walking`() {
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { okSteps(it, isActive = false) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertFalse("locked and not walking = inactive", s.isUserActive)
        vm.track(emptyList())
    }

    @Test fun `red dot when offline regardless of lock or walking`() {
        answer = { ok(it, online = false, locked = false) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertFalse("offline = always inactive", s.isUserActive)
        vm.track(emptyList())
    }

    @Test fun `green dot when online and unlocked and walking`() {
        answer = { ok(it, online = true, locked = false) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        val s = vm.statuses.value.getValue("p1")
        assertTrue("unlocked + walking = active", s.isUserActive)
        vm.track(emptyList())
    }

    // -- Walking status transition tests (HomeScreen green dot) --

    @Test fun `green dot when locked but walking`() {
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertTrue("locked+walking = green dot", vm.statuses.value.getValue("p1").isUserActive)
        vm.track(emptyList())
    }

    @Test fun `red dot when walking stops and phone is locked`() {
        // First poll: walking
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertTrue("initially walking", vm.statuses.value.getValue("p1").isUserActive)

        // Next poll: stopped walking
        stepsAnswer = { okSteps(it, isActive = false) }
        waitOnePoll()
        assertFalse("stopped walking + locked = red dot", vm.statuses.value.getValue("p1").isUserActive)
        vm.track(emptyList())
    }

    @Test fun `green dot stays when walking stops but phone is unlocked`() {
        answer = { ok(it, online = true, locked = false) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()

        // Stop walking but phone still unlocked
        stepsAnswer = { okSteps(it, isActive = false) }
        waitOnePoll()
        assertTrue("stopped walking but unlocked = still green", vm.statuses.value.getValue("p1").isUserActive)
        vm.track(emptyList())
    }

    @Test fun `steps fetch failure does not crash - defaults to not active`() {
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { throw IOException("timeout") }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertFalse("steps failure + locked = red dot", vm.statuses.value.getValue("p1").isUserActive)
        vm.track(emptyList())
    }

    @Test fun `steps fetch failure keeps the last known walking state instead of flipping red`() {
        answer = { ok(it, online = true, locked = true) }
        stepsAnswer = { okSteps(it, isActive = true) }
        val vm = newViewModel()
        vm.track(listOf("p1")); runPollNow()
        assertTrue(vm.statuses.value.getValue("p1").isActive)

        stepsAnswer = { throw IOException("blip") }
        waitOnePoll()
        assertTrue("one failed request must not flip a walking friend to red", vm.statuses.value.getValue("p1").isActive)
        vm.track(emptyList())
    }
}

package com.Consnow.features.steps.presentation

import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.PeerActivityResponse
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
            if (name != "getPeerActivity") throw AssertionError("Unexpected API call: $name")
            calls++
            answer(args[0] as String) // args[1] is the coroutine Continuation
        }
        val repo = fake<StepsRepository> { name, _ -> throw AssertionError("Unexpected repo call: $name") }
        return PeerStatusesViewModel(GetPeerStepsUseCase(repo), api)
    }

    private fun ok(id: String, online: Boolean, locked: Boolean, unlockedAt: String? = null) =
        Response.success(PeerActivityResponse(userId = id, unlockedAt = unlockedAt, isLocked = locked, isOnline = online))

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
}

package com.Consnow.features.steps.presentation

import com.Consnow.core.common.Result
import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.PeerActivityResponse
import com.Consnow.features.steps.data.repository.PeerStepsResult
import com.Consnow.features.steps.domain.StepsRepository
import com.Consnow.features.steps.domain.usecase.GetPeerStepsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy

/**
 * The offline symbol on the friend screen. It must show ONLY when the server says the friend
 * is offline - never while still unknown, and never because MY request failed.
 * The lock behaviour (offline => shown as locked) must stay exactly as it was.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PeerStepsViewModelOfflineTest {

    private val scheduler = TestCoroutineScheduler()
    private var nextId = 0
    private var activity: (String) -> Response<PeerActivityResponse> = { ok(it, online = true, locked = false) }

    private inline fun <reified T> fake(noinline handler: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
            when (method.name) {
                "toString" -> "Fake"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> handler(method.name, args ?: emptyArray())
            }
        } as T

    private fun ok(id: String, online: Boolean, locked: Boolean) =
        Response.success(PeerActivityResponse(userId = id, unlockedAt = null, isLocked = locked, isOnline = online))

    private fun newViewModel(peerId: String): PeerStepsViewModel {
        val repo = fake<StepsRepository> { name, _ ->
            if (name != "getPeerSteps") throw AssertionError("Unexpected repo call: $name")
            Result.Success(PeerStepsResult("Peer", 100, "2026-11-01", false, null))
        }
        val api = fake<ApiService> { name, args ->
            if (name != "getPeerActivity") throw AssertionError("Unexpected API call: $name")
            activity(args[0] as String)
        }
        return PeerStepsViewModel(GetPeerStepsUseCase(repo), api, peerId)
    }

    private fun pollOnce() { scheduler.advanceTimeBy(15_000); scheduler.runCurrent() }

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher(scheduler))
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `not shown before the first answer from the server`() {
        val vm = newViewModel("off-${nextId++}")
        assertFalse("unknown must not look offline", vm.friendOffline.value)
    }

    @Test fun `shown when the server says the friend is offline`() {
        activity = { ok(it, online = false, locked = false) }
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertTrue(vm.friendOffline.value)
    }

    @Test fun `not shown when the friend is online`() {
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertFalse(vm.friendOffline.value)
    }

    @Test fun `not shown when MY request fails (my own internet is down)`() {
        activity = { throw IOException("no internet") }
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertFalse("my network problem is not the friend being offline", vm.friendOffline.value)
    }

    @Test fun `friend goes offline later, then comes back`() {
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertFalse(vm.friendOffline.value)

        activity = { ok(it, online = false, locked = false) }
        pollOnce()
        assertTrue("shown after the next poll reports offline", vm.friendOffline.value)

        activity = { ok(it, online = true, locked = false) }
        pollOnce()
        assertFalse("hidden again once the friend is back", vm.friendOffline.value)
    }

    @Test fun `my request failing keeps the last known friend state`() {
        activity = { ok(it, online = false, locked = false) }
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertTrue(vm.friendOffline.value)

        activity = { throw IOException("blip") }
        pollOnce()
        assertTrue("a failed request says nothing new about the friend", vm.friendOffline.value)
    }

    @Test fun `lock behaviour is unchanged - an offline friend is still reported as locked`() {
        activity = { ok(it, online = false, locked = false) }
        val vm = newViewModel("off-${nextId++}"); scheduler.runCurrent()
        assertTrue(vm.isLocked.value)
        assertFalse(vm.isOnline.value)
    }
}

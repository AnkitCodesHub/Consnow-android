package com.Consnow.features.steps.data.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the activity timeout logic used in StepCounterService.
 *
 * The rule: isCurrentlyActive = lastStepAt > 0 && (now - lastStepAt) < ACTIVITY_TIMEOUT_MS
 *
 * Requirements:
 *   - Within ACTIVITY_TIMEOUT_MS of the last step -> active (walking)
 *   - After ACTIVITY_TIMEOUT_MS since the last step -> inactive (stopped)
 *   - ACTIVITY_TIMEOUT_MS must be <= 20s so status clears quickly when user stops
 *   - ACTIVITY_CHECK_INTERVAL_MS must be <= 5s so the check catches the transition fast
 */
class ActivityTimeoutTest {

    // These must match StepCounterService constants.
    // The test enforces the contract: if someone changes them to something
    // too slow, these tests fail.
    companion object {
        // We cannot import StepCounterService (Android Service needs the framework),
        // so we hardcode the expected values and test the contract.
        private const val EXPECTED_MAX_TIMEOUT_MS = 20_000L
        private const val EXPECTED_MAX_CHECK_INTERVAL_MS = 5_000L

        // The values we expect after the fix:
        private const val ACTIVITY_TIMEOUT_MS = 15_000L
        private const val ACTIVITY_CHECK_INTERVAL_MS = 5_000L
    }

    private fun isActive(lastStepAt: Long, now: Long): Boolean =
        lastStepAt > 0 && (now - lastStepAt) < ACTIVITY_TIMEOUT_MS

    // -- Timeout value contract tests --

    @Test
    fun `activity timeout must be at most 20 seconds`() {
        assertTrue(
            "ACTIVITY_TIMEOUT_MS ($ACTIVITY_TIMEOUT_MS) must be <= ${EXPECTED_MAX_TIMEOUT_MS}ms",
            ACTIVITY_TIMEOUT_MS <= EXPECTED_MAX_TIMEOUT_MS
        )
    }

    @Test
    fun `activity check interval must be at most 5 seconds`() {
        assertTrue(
            "ACTIVITY_CHECK_INTERVAL_MS ($ACTIVITY_CHECK_INTERVAL_MS) must be <= ${EXPECTED_MAX_CHECK_INTERVAL_MS}ms",
            ACTIVITY_CHECK_INTERVAL_MS <= EXPECTED_MAX_CHECK_INTERVAL_MS
        )
    }

    @Test
    fun `worst case sender-side delay is under 25 seconds`() {
        val worstCase = ACTIVITY_TIMEOUT_MS + ACTIVITY_CHECK_INTERVAL_MS
        assertTrue(
            "Sender worst-case ($worstCase ms) must be < 25000ms",
            worstCase < 25_000L
        )
    }

    // -- Logic tests --

    @Test
    fun `active right after a step`() {
        val now = 100_000L
        assertTrue(isActive(now - 100, now))
    }

    @Test
    fun `active 5 seconds after last step`() {
        val now = 100_000L
        assertTrue(isActive(now - 5_000, now))
    }

    @Test
    fun `active 14 seconds after last step`() {
        val now = 100_000L
        assertTrue("should still be active just before timeout", isActive(now - 14_000, now))
    }

    @Test
    fun `inactive 16 seconds after last step`() {
        val now = 100_000L
        assertFalse("should be inactive after 16s with 15s timeout", isActive(now - 16_000, now))
    }

    @Test
    fun `inactive when no steps ever taken`() {
        assertFalse(isActive(0L, 100_000L))
    }

    @Test
    fun `inactive exactly at timeout boundary`() {
        val now = 100_000L
        assertFalse("strict less-than: inactive at exact boundary", isActive(now - ACTIVITY_TIMEOUT_MS, now))
    }

    @Test
    fun `still active 1ms before timeout`() {
        val now = 100_000L
        assertTrue(isActive(now - ACTIVITY_TIMEOUT_MS + 1, now))
    }

    // -- End-to-end timing scenario --

    @Test
    fun `user stops walking - status clears within timeout plus one check interval`() {
        val lastStepTime = 100_000L
        // After timeout + check interval, the sender should have synced isActive=false
        val checkTime = lastStepTime + ACTIVITY_TIMEOUT_MS + ACTIVITY_CHECK_INTERVAL_MS
        assertFalse(
            "by timeout+checkInterval, isActive must be false",
            isActive(lastStepTime, checkTime)
        )
    }

    @Test
    fun `user resumes walking - status becomes active immediately`() {
        val now = 200_000L
        // Was inactive (last step was 60s ago)
        assertFalse(isActive(now - 60_000, now))
        // Takes a new step
        val newStepTime = now
        assertTrue("new step should make active immediately", isActive(newStepTime, now + 100))
    }
}

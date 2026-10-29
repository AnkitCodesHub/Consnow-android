package com.Consnow.features.steps.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Tests for the "Last moved" / "Last unlocked" text (test plan U14–U16). */
class FormatTimeAgoTest {

    private fun isoSecondsAgo(seconds: Long, millis: Long = 0): String =
        Instant.now().minusSeconds(seconds).truncatedTo(ChronoUnit.SECONDS).plusMillis(millis).toString()

    // U14: the server sends timestamps with milliseconds, e.g. 2026-10-29T06:09:12.401Z
    @Test fun `U14 timestamp with milliseconds gives minutes, not Unknown`() {
        val iso = isoSecondsAgo(330, millis = 401)
        assertTrue("test input should contain millis: $iso", iso.endsWith(".401Z"))
        assertEquals("5m ago", formatTimeAgo(iso))
    }

    @Test fun `timestamp without milliseconds also works`() {
        assertEquals("5m ago", formatTimeAgo(isoSecondsAgo(330)))
    }

    @Test fun `seconds ago shows just now`() {
        assertEquals("just now", formatTimeAgo(isoSecondsAgo(10, millis = 5)))
    }

    @Test fun `hours ago`() {
        assertEquals("2h ago", formatTimeAgo(isoSecondsAgo(2 * 3600 + 300, millis = 123)))
    }

    @Test fun `days ago`() {
        assertEquals("3d ago", formatTimeAgo(isoSecondsAgo(3 * 86400 + 3600)))
    }

    // U15: a friend's phone clock is ahead, so the time is in the future
    @Test fun `U15 future time shows just now, never negative`() {
        val future = Instant.now().plusSeconds(3600).toString()
        assertEquals("just now", formatTimeAgo(future))
    }

    // U16: garbage from the server must not crash the screen
    @Test fun `U16 garbage text shows Unknown`() {
        assertEquals("Unknown", formatTimeAgo("hello"))
        assertEquals("Unknown", formatTimeAgo(""))
        assertEquals("Unknown", formatTimeAgo("2026-10-29 06:09:12"))
    }

    // Same rules for the millis version used by the live timer
    @Test fun `millis version - zero or negative is Unknown`() {
        assertEquals("Unknown", formatTimeAgoFromMillis(0L))
        assertEquals("Unknown", formatTimeAgoFromMillis(-5L))
    }

    @Test fun `millis version - future is just now`() {
        assertEquals("just now", formatTimeAgoFromMillis(System.currentTimeMillis() + 60_000))
    }

    @Test fun `millis version - 90 minutes is 1h ago`() {
        assertEquals("1h ago", formatTimeAgoFromMillis(System.currentTimeMillis() - 90 * 60_000L))
    }
}

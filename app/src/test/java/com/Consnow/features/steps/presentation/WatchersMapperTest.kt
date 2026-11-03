package com.Consnow.features.steps.presentation

import com.Consnow.core.network.dto.ProfileViewResponse
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class WatchersMapperTest {

    @Test
    fun `server rows become watchers with the right time`() {
        val rows = listOf(ProfileViewResponse("id1", "Ankit", "2026-11-03T09:18:55.540Z"))
        val w = rows.toWatchers().single()
        assertEquals("Ankit", w.name)
        assertEquals(java.time.Instant.parse("2026-11-03T09:18:55.540Z").toEpochMilli(), w.openedAtMs)
    }

    @Test
    fun `a row with a broken time is skipped, the rest are kept`() {
        val rows = listOf(
            ProfileViewResponse("a", "Bad", "garbage"),
            ProfileViewResponse("b", "Good", "2026-11-03T09:00:00Z"),
        )
        assertEquals(listOf("Good"), rows.toWatchers().map { it.name })
    }

    @Test
    fun `start of local day uses the phone's time zone (India)`() {
        // 4 Nov 01:30 in India = 3 Nov 20:00 UTC, so "today" began 3 Nov 18:30 UTC
        val now = java.time.Instant.parse("2026-11-03T20:00:00Z").toEpochMilli()
        assertEquals("2026-11-03T18:30:00Z", startOfLocalDayIso(now, ZoneId.of("Asia/Kolkata")))
    }

    @Test
    fun `start of local day in UTC is midnight UTC`() {
        val now = java.time.Instant.parse("2026-11-03T20:00:00Z").toEpochMilli()
        assertEquals("2026-11-03T00:00:00Z", startOfLocalDayIso(now, ZoneId.of("UTC")))
    }
}

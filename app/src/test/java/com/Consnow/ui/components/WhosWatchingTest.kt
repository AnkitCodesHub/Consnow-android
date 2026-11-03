package com.Consnow.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhosWatchingTest {

    @Test
    fun `shows at most 3 watchers`() {
        val all = (1..8).map { ProfileWatcher("Friend$it", it * 1_000L) }
        assertEquals(3, visibleWatchers(all).size)
    }

    @Test
    fun `most recent opener comes first`() {
        val all = listOf(
            ProfileWatcher("Old", 1_000L),
            ProfileWatcher("New", 9_000L),
            ProfileWatcher("Mid", 5_000L),
        )
        assertEquals(listOf("New", "Mid", "Old"), visibleWatchers(all).map { it.name })
    }

    @Test
    fun `keeps only the 3 most recent when there are more`() {
        val all = (1..7).map { ProfileWatcher("F$it", it * 1_000L) }
        assertEquals(listOf("F7", "F6", "F5"), visibleWatchers(all).map { it.name })
    }

    @Test
    fun `the same friend can appear in several rows`() {
        val all = listOf(
            ProfileWatcher("Ankit", 9_000L),
            ProfileWatcher("Ravi", 8_000L),
            ProfileWatcher("Ankit", 7_000L),
        )
        assertEquals(listOf("Ankit", "Ravi", "Ankit"), visibleWatchers(all).map { it.name })
    }

    @Test
    fun `empty list stays empty`() {
        assertTrue(visibleWatchers(emptyList()).isEmpty())
    }

    @Test
    fun `hold time is under one second`() {
        assertTrue(WATCHERS_HOLD_MS in 300L..999L)
    }

    @Test
    fun `card shrinks while pressed, grows while held, normal otherwise`() {
        assertEquals(1f, cardScale(pressed = false, held = false), 0.0001f)
        assertTrue(cardScale(pressed = true, held = false) < 1f)
        assertTrue(cardScale(pressed = true, held = true) > 1f)
        assertTrue(cardScale(pressed = false, held = true) > 1f)
    }

    @Test
    fun `only the first name is shown`() {
        assertEquals("Sangita", firstName("Sangita Pandey"))
        assertEquals("Ravi", firstName("  Ravi   Kumar  "))
        assertEquals("Ankit", firstName("Ankit"))
        assertEquals("Mary", firstName("Mary Jane Watson"))
    }

    @Test
    fun `blank name gives an empty first name`() {
        assertEquals("", firstName(""))
        assertEquals("", firstName("   "))
    }
}

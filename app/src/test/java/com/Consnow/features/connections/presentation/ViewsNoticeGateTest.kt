package com.Consnow.features.connections.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewsNoticeGateTest {

    private class FakeStore(var shown: Boolean = false) : FlagStore {
        override fun isSet() = shown
        override fun set() { shown = true }
    }

    @Test
    fun `first time the notice is shown`() {
        assertTrue(ViewsNoticeGate(FakeStore()).consume())
    }

    @Test
    fun `it is never shown a second time`() {
        val store = FakeStore()
        assertTrue(ViewsNoticeGate(store).consume())
        assertFalse(ViewsNoticeGate(store).consume()) // e.g. next app start
    }

    @Test
    fun `already seen in an earlier session means never shown`() {
        assertFalse(ViewsNoticeGate(FakeStore(shown = true)).consume())
    }

    @Test
    fun `notice stays on screen for a few seconds`() {
        assertTrue(NOTICE_VISIBLE_MS in 3_000L..10_000L)
        assertEquals(6_000L, NOTICE_VISIBLE_MS)
    }
}

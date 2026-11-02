package com.Consnow.features.steps.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Step count must be exact in every login / reinstall / clock case. */
class StepCounterLogicTest {

    private val d1 = "2026-11-02"
    private val d2 = "2026-11-03"

    private fun CounterState.read(total: Int, day: String = d1) =
        StepCounterLogic.onReading(this, total.toFloat(), day)
    private fun CounterState.claim(user: String = "me") = StepCounterLogic.claimFor(this, user)
    private fun CounterState.merge(server: Int, day: String = d1) =
        StepCounterLogic.mergeServer(this, server, day, day)!!

    @Test fun `counts steps from the first reading`() {
        assertEquals(10, CounterState(owner = "me").read(1000).read(1003).read(1010).lastCount)
    }

    @Test fun `reinstall - server count plus steps walked before the server answered`() {
        var s = CounterState(freshSinceWipe = true).claim().read(1000).read(1003)
        s = s.merge(5000)
        assertEquals(5003, s.lastCount)
        assertEquals(5005, s.read(1005).lastCount)
    }

    @Test fun `reinstall - merge before any step`() {
        var s = CounterState(freshSinceWipe = true).claim().merge(5000)
        assertEquals(5000, s.lastCount)
        s = s.read(1000); assertEquals(5000, s.lastCount)
        s = s.read(1004); assertEquals(5004, s.lastCount)
    }

    @Test fun `logging in again after a merge never double counts`() {
        var s = CounterState(freshSinceWipe = true).claim().read(1000).read(1003).merge(5000) // 5003
        s = s.claim().merge(5003)              // phone synced 5003, user logs in again
        assertEquals(5003, s.lastCount)
        assertEquals(5004, s.read(1004).lastCount)
    }

    @Test fun `log out and back in keeps the phone count when the server is behind`() {
        var s = CounterState(owner = "me").read(1000).read(6000)   // 5000 today
        s = s.claim().merge(4990)
        assertEquals(5000, s.lastCount)
        assertEquals(5001, s.read(6001).lastCount)
    }

    @Test fun `server ahead of the phone raises the count once`() {
        var s = CounterState(owner = "me").read(1000).read(1100)   // 100
        s = s.claim().merge(300)
        assertEquals(300, s.lastCount)
        s = s.read(1101); assertEquals(301, s.lastCount)
        assertEquals(301, s.claim().merge(300).lastCount)           // merging again: no change
    }

    @Test fun `another account on the same phone starts from its own count`() {
        var s = CounterState(owner = "alice").read(1000).read(7000) // alice: 6000
        s = s.claim("bob")
        assertEquals(0, s.lastCount)
        s = s.read(7005).merge(200)                                  // bob walks 5, server has 200
        assertEquals(205, s.lastCount)
    }

    @Test fun `app update from an older version is not treated as a reinstall`() {
        // the old version saved a count but no owner and no fresh flag
        val old = CounterState(date = d1, initial = 1000f, lastTotal = 1500f, lastCount = 500)
        assertEquals(500, old.claim().merge(500).lastCount)
    }

    @Test fun `new day starts from zero`() {
        var s = CounterState(owner = "me").read(1000).read(9000)    // 8000 on day 1
        s = s.read(9010, d2); assertEquals(0, s.lastCount)
        s = s.read(9015, d2); assertEquals(5, s.lastCount)
    }

    @Test fun `midnight while the phone is still, then walking`() {
        var s = StepCounterLogic.onDayCheck(CounterState(owner = "me").read(1000).read(9000), d2)
        assertEquals(0, s.lastCount)
        s = s.read(9003, d2); assertEquals(0, s.lastCount)
        s = s.read(9006, d2); assertEquals(3, s.lastCount)
    }

    @Test fun `phone reboot keeps today's steps`() {
        var s = CounterState(owner = "me").read(1000).read(5000)    // 4000
        s = s.read(10); assertEquals(4010, s.lastCount)
        s = s.read(20); assertEquals(4020, s.lastCount)
    }

    @Test fun `count saved under a future date moves back to today`() {
        // clock set to 5 Nov, 40 steps, clock set back to 2 Nov, 5 more steps
        val s = CounterState(owner = "me").read(1000, "2026-11-05").read(1040, "2026-11-05").read(1045, d1)
        assertEquals(d1, s.date)
        assertEquals(45, s.lastCount)
        val idle = StepCounterLogic.onDayCheck(CounterState(owner = "me", date = "2026-11-05", lastCount = 40), d1)
        assertEquals(d1, idle.date)
        assertEquals(40, idle.lastCount)
    }

    @Test fun `server answer for a different day is ignored`() {
        assertNull(StepCounterLogic.mergeServer(CounterState(owner = "me"), 5000, d1, d2))
    }

    @Test fun `merge on a new day before the first reading`() {
        var s = CounterState(owner = "me").read(1000).read(9000).claim()  // 8000 yesterday
        s = StepCounterLogic.mergeServer(s, 0, d2, d2)!!
        assertEquals(d2, s.date)
        assertEquals(0, s.lastCount)
        s = s.read(9004, d2); assertEquals(0, s.lastCount)
        s = s.read(9010, d2); assertEquals(6, s.lastCount)
    }
}

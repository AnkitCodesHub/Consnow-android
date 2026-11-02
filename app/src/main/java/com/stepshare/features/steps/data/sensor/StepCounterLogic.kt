package com.Consnow.features.steps.data.sensor

/**
 * Today's step-count bookkeeping. Plain Kotlin (no Android types) so it is unit tested
 * in StepCounterLogicTest.
 *
 *     todaySteps = (sensorTotal - initial) + accumulated
 *
 *  sensorTotal  hardware TYPE_STEP_COUNTER value = steps since the phone booted
 *  initial      sensorTotal when the current baseline was taken (null = take it at the next reading)
 *  accumulated  steps counted before that baseline (earlier boot today, or merged in from the server)
 */
data class CounterState(
    val owner: String? = null,          // userId these steps belong to
    val date: String? = null,           // YYYY-MM-DD the count is for
    val initial: Float? = null,
    val accumulated: Int = 0,
    val lastTotal: Float = -1f,         // last raw sensor value seen, -1 = none yet
    val lastCount: Int = 0,             // today's steps as of the last reading
    // Counting started on a phone with no saved data (reinstall / cleared data / another
    // account): the server's count must be ADDED to it. Otherwise the higher one wins.
    val freshSinceWipe: Boolean = false,
)

object StepCounterLogic {

    /** A new reading from the step sensor. */
    fun onReading(state: CounterState, total: Float, today: String): CounterState {
        val d = state.date
        val dated = if (d == null) {
            state.copy(date = today)
        } else if (d < today) {
            return finish(newDay(state, today, initial = total), total)
        } else if (d > today) {
            // Saved date is in the future: the clock was set ahead earlier. These steps were
            // really walked today, so they stay on today.
            state.copy(date = today)
        } else {
            state
        }
        val rebased = if (dated.lastTotal >= 0f && total < dated.lastTotal) {
            // Sensor went backwards = phone rebooted (it restarts at 0). Keep today's steps.
            dated.copy(initial = 0f, accumulated = dated.lastCount)
        } else {
            dated
        }
        return finish(rebased, total)
    }

    /** Date check while no steps arrive (midnight passed while the phone was still). */
    fun onDayCheck(state: CounterState, today: String): CounterState {
        val d = state.date
        return if (d == null) state.copy(date = today)
        else if (d < today) newDay(state, today, initial = null)
        else if (d > today) state.copy(date = today)
        else state
    }

    /** Right after login: makes the count belong to [userId]. */
    fun claimFor(state: CounterState, userId: String): CounterState = when (state.owner) {
        userId -> state
        null -> state.copy(owner = userId)
        // Another account used this phone: this one starts from its own count (merged next)
        else -> CounterState(
            owner = userId,
            date = state.date,
            initial = if (state.lastTotal >= 0f) state.lastTotal else null,
            accumulated = 0,
            lastTotal = state.lastTotal,
            lastCount = 0,
            freshSinceWipe = true,
        )
    }

    /**
     * Merges the server's count for [serverDate] into today's count.
     *  - fresh (phone data was wiped): server count + steps counted on the phone since
     *  - otherwise: whichever is higher
     * Never lowers the count. Returns null if [serverDate] is no longer today (retry).
     */
    fun mergeServer(state: CounterState, serverCount: Int, serverDate: String, today: String): CounterState? {
        if (serverDate != today) return null
        val s = onDayCheck(state, today)
        val server = serverCount.coerceAtLeast(0)
        val local = s.lastCount
        val target = if (s.freshSinceWipe) local + server else maxOf(local, server)
        // Raising `accumulated` by the difference raises the count by exactly that much,
        // whatever the current baseline is, so no step is counted twice.
        return s.copy(
            accumulated = s.accumulated + (target - local),
            lastCount = target,
            freshSinceWipe = false,
        )
    }

    private fun newDay(s: CounterState, today: String, initial: Float?) = CounterState(
        owner = s.owner,
        date = today,
        initial = initial,
        accumulated = 0,
        lastTotal = s.lastTotal,
        lastCount = 0,
        freshSinceWipe = false,
    )

    private fun finish(s: CounterState, total: Float): CounterState {
        val initial = s.initial ?: total
        val count = (total - initial).toInt().coerceAtLeast(0) + s.accumulated
        return s.copy(initial = initial, lastTotal = total, lastCount = count)
    }
}

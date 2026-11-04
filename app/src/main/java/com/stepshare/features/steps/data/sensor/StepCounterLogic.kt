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
    // Android's reboot counter (Settings.Global.BOOT_COUNT) when lastTotal was read, null = unknown.
    // Tells a real reboot apart from a sensor that briefly reports a low value without one.
    val bootCount: Int? = null,
    // Wall-clock ms of the last reading, used to reject impossible jumps. 0 = unknown.
    val lastReadingAt: Long = 0L,
)

object StepCounterLogic {

    // More than anyone can walk or run, plus room for readings the sensor delivers in a batch
    const val MAX_STEPS_PER_SECOND = 5
    const val JUMP_SLACK_STEPS = 500

    /**
     * A new reading from the step sensor.
     * [boot] = current Settings.Global.BOOT_COUNT (null if unknown), [nowMs] = wall-clock ms (0 if unknown).
     */
    fun onReading(
        state: CounterState,
        total: Float,
        today: String,
        boot: Int? = null,
        nowMs: Long = 0L,
    ): CounterState {
        val d = state.date
        val dated = if (d == null) {
            state.copy(date = today)
        } else if (d < today) {
            return finish(newDay(state, today, initial = total), total, boot, nowMs)
        } else if (d > today) {
            // Saved date is in the future: the clock was set ahead earlier. These steps were
            // really walked today, so they stay on today.
            state.copy(date = today)
        } else {
            state
        }
        // With the boot counter known, only a real reboot counts as one. Without it, fall back
        // to "the sensor went backwards".
        val rebooted = if (boot != null && dated.bootCount != null) boot != dated.bootCount
                       else total < dated.lastTotal
        val rebased = if (dated.lastTotal >= 0f && rebooted) {
            // Phone rebooted (the sensor restarts at 0). Keep today's steps.
            dated.copy(initial = 0f, accumulated = dated.lastCount)
        } else if (dated.lastTotal >= 0f && total < dated.lastTotal) {
            // Lower value but no reboot: a sensor glitch (some phones send one when the service
            // restarts). Ignore it. Treating it as a reboot set the baseline to 0, so the next
            // normal reading added every step since boot to today (millions of steps).
            return dated
        } else {
            dated
        }
        return guardJump(dated, finish(rebased, total, boot, nowMs), total, nowMs)
    }

    /**
     * Second line of defence: if a reading raises today's count by more than anyone could
     * walk since the previous reading, it is not counted. Today's count stays as it was and
     * counting continues from this reading.
     */
    private fun guardJump(prev: CounterState, next: CounterState, total: Float, nowMs: Long): CounterState {
        if (nowMs <= 0L || prev.lastReadingAt <= 0L) return next // no times to judge by
        val gained = (next.lastCount - prev.lastCount).toLong()
        val seconds = (nowMs - prev.lastReadingAt).coerceAtLeast(0L) / 1000
        val allowed = seconds * MAX_STEPS_PER_SECOND + JUMP_SLACK_STEPS
        if (gained <= allowed) return next
        return next.copy(initial = total, accumulated = prev.lastCount, lastCount = prev.lastCount)
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
            bootCount = state.bootCount,
            lastReadingAt = state.lastReadingAt,
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
        bootCount = s.bootCount,
        lastReadingAt = s.lastReadingAt,
    )

    private fun finish(s: CounterState, total: Float, boot: Int?, nowMs: Long): CounterState {
        val initial = s.initial ?: total
        val count = (total - initial).toInt().coerceAtLeast(0) + s.accumulated
        return s.copy(
            initial = initial,
            lastTotal = total,
            lastCount = count,
            bootCount = boot ?: s.bootCount,
            lastReadingAt = if (nowMs > 0L) nowMs else s.lastReadingAt,
        )
    }
}

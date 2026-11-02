package com.Consnow.core.common

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId

/**
 * Wall-clock time that changing the phone's date/time by hand can't move.
 *
 * Every API response carries the server's time (HTTP "Date" header). We store it together
 * with SystemClock.elapsedRealtime() at that moment. elapsedRealtime counts up since boot
 * and ignores clock changes, so
 *     now = serverTime + (elapsedNow - elapsedThen)
 * stays correct even if the user sets the date ahead or back. Until the first server
 * response after a reboot it falls back to the phone clock.
 */
object TrustedClock {
    private const val PREFS = "trusted_clock"
    private const val KEY_WALL = "anchor_wall"
    private const val KEY_ELAPSED = "anchor_elapsed"
    private const val KEY_BOOT = "anchor_boot"

    private val lock = Any()
    private var appContext: Context? = null
    private var bootCount = -1            // this boot (a process can't outlive a reboot)
    private var anchorWall = -1L
    private var anchorElapsed = -1L
    private var anchorBoot = -2

    fun init(context: Context) {
        val ctx = context.applicationContext
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        synchronized(lock) {
            appContext = ctx
            bootCount = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)
            anchorWall = p.getLong(KEY_WALL, -1L)
            anchorElapsed = p.getLong(KEY_ELAPSED, -1L)
            anchorBoot = p.getInt(KEY_BOOT, -2)
        }
    }

    /** Server time from an API response. */
    fun onServerTime(serverMs: Long) {
        val ctx = synchronized(lock) { appContext } ?: return
        val elapsed = SystemClock.elapsedRealtime()
        val boot = synchronized(lock) {
            anchorWall = serverMs
            anchorElapsed = elapsed
            anchorBoot = bootCount
            bootCount
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_WALL, serverMs)
            .putLong(KEY_ELAPSED, elapsed)
            .putInt(KEY_BOOT, boot)
            .apply()
    }

    fun nowMs(): Long = synchronized(lock) {
        // Not initialised (plain JVM unit tests) or no server time yet: phone clock
        if (appContext == null || anchorWall <= 0L) return System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        // Anchor from before a reboot: elapsedRealtime restarted, so it no longer applies
        if (anchorBoot != bootCount || elapsed < anchorElapsed) return System.currentTimeMillis()
        anchorWall + (elapsed - anchorElapsed)
    }

    /** Today's date (YYYY-MM-DD) in the phone's time zone, on the trusted clock. */
    fun today(): String =
        Instant.ofEpochMilli(nowMs()).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}

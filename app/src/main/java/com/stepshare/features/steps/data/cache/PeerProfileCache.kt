package com.Consnow.features.steps.data.cache

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Last known snapshot of one friend's profile, used to draw PeerStepsScreen instantly. */
data class CachedPeerProfile(
    val name: String = "",
    val stepCount: Int? = null,       // null = steps never fetched for this friend
    val stepDate: String? = null,     // ISO date the stepCount belongs to
    val isActive: Boolean = false,
    val lastActiveAt: String? = null,
    val hasActivity: Boolean = false, // true once lock/online status was fetched at least once
    val isOnline: Boolean = false,
    val isLocked: Boolean = true,
    val lastUnlockedAtMs: Long = 0L,
) {
    /** Steps for today, or 0 if the cached count is from an earlier day (midnight reset). */
    fun todaySteps(): Int? = when {
        stepCount == null -> null
        stepDate == LocalDate.now().toString() -> stepCount
        else -> 0
    }
}

/**
 * Process-wide cache of friends' profiles: memory first, persisted to SharedPreferences
 * so it also survives an app restart.
 *
 * Written by PeerStatusesViewModel (Home list polling), the Home prefetch, and PeerStepsViewModel.
 * Read by PeerStepsViewModel on creation so the friend's profile shows with no spinner,
 * then the network refresh replaces it silently.
 *
 * Works memory-only until [init] is called (that keeps unit tests free of Android).
 */
object PeerProfileCache {
    private const val PREFS = "peer_profile_cache"
    private const val KEY = "profiles"

    private val memory = ConcurrentHashMap<String, CachedPeerProfile>()
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        loadFrom(p)
    }

    fun get(peerId: String): CachedPeerProfile? = memory[peerId]

    fun putName(peerId: String, name: String) {
        if (name.isBlank()) return
        update(peerId) { it.copy(name = name) }
    }

    fun putSteps(
        peerId: String,
        name: String,
        stepCount: Int,
        date: String,
        isActive: Boolean,
        lastActiveAt: String?,
    ) = update(peerId) {
        it.copy(
            name = name.ifBlank { it.name },
            stepCount = stepCount,
            stepDate = date,
            isActive = isActive,
            lastActiveAt = lastActiveAt,
        )
    }

    /** Save lock/online status. The unlock time only ever moves forward. */
    fun putActivity(peerId: String, isOnline: Boolean, isLocked: Boolean, lastUnlockedAtMs: Long?) =
        update(peerId) {
            it.copy(
                hasActivity = true,
                isOnline = isOnline,
                isLocked = isLocked,
                lastUnlockedAtMs = maxOf(it.lastUnlockedAtMs, lastUnlockedAtMs ?: 0L),
            )
        }

    /** Call on logout so the next account on this phone never sees the old friends' data. */
    fun clear() {
        memory.clear()
        prefs?.edit()?.remove(KEY)?.apply()
    }

    private fun update(peerId: String, change: (CachedPeerProfile) -> CachedPeerProfile) {
        memory.compute(peerId) { _, old -> change(old ?: CachedPeerProfile()) }
        persist()
    }

    private fun persist() {
        val p = prefs ?: return
        val root = JSONObject()
        memory.forEach { (id, v) ->
            root.put(id, JSONObject().apply {
                put("name", v.name)
                v.stepCount?.let { put("stepCount", it) }
                v.stepDate?.let { put("stepDate", it) }
                put("isActive", v.isActive)
                v.lastActiveAt?.let { put("lastActiveAt", it) }
                put("hasActivity", v.hasActivity)
                put("isOnline", v.isOnline)
                put("isLocked", v.isLocked)
                put("lastUnlockedAtMs", v.lastUnlockedAtMs)
            })
        }
        p.edit().putString(KEY, root.toString()).apply()
    }

    private fun loadFrom(p: SharedPreferences) {
        val json = p.getString(KEY, null) ?: return
        try {
            val root = JSONObject(json)
            root.keys().forEach { id ->
                val o = root.getJSONObject(id)
                memory.putIfAbsent(
                    id,
                    CachedPeerProfile(
                        name = o.optString("name", ""),
                        stepCount = if (o.has("stepCount")) o.getInt("stepCount") else null,
                        stepDate = o.optString("stepDate").ifEmpty { null },
                        isActive = o.optBoolean("isActive", false),
                        lastActiveAt = o.optString("lastActiveAt").ifEmpty { null },
                        hasActivity = o.optBoolean("hasActivity", false),
                        isOnline = o.optBoolean("isOnline", false),
                        isLocked = o.optBoolean("isLocked", true),
                        lastUnlockedAtMs = o.optLong("lastUnlockedAtMs", 0L),
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt cache: ignore, it will be rebuilt from the network
        }
    }
}

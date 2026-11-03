package com.Consnow.features.steps.presentation

import com.Consnow.core.network.api.ApiService
import com.Consnow.core.network.dto.ProfileViewResponse
import com.Consnow.ui.components.ProfileWatcher
import java.time.Instant
import java.time.ZoneId

/** Server rows -> popup rows. A row with an unreadable time is skipped. */
fun List<ProfileViewResponse>.toWatchers(): List<ProfileWatcher> = mapNotNull {
    try {
        ProfileWatcher(it.name, Instant.parse(it.viewedAt).toEpochMilli())
    } catch (_: Exception) {
        null
    }
}

/** ISO time of 00:00 today on this phone — "today" for the list. */
fun startOfLocalDayIso(
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toString()

/** Loads today's viewers; null when offline or the server failed, so the caller keeps what it had. */
suspend fun fetchWatchers(api: ApiService): List<ProfileWatcher>? = try {
    val r = api.getProfileViews(startOfLocalDayIso())
    if (r.isSuccessful) r.body()?.toWatchers() else null
} catch (_: Exception) {
    null
}

package com.Consnow.features.connections.presentation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.Consnow.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Consnow.features.connections.domain.model.IncomingRequest
import com.Consnow.features.connections.domain.model.PeerConnectionStatus
import com.Consnow.features.connections.domain.model.PeerInfo
import kotlinx.coroutines.delay

// ── Colors ────────────────────────────────────────────────────────────────

/**
 * connect: green for text/badges; connectSolid: the filled button (white icon stays readable
 * in dark mode too); disconnect / neutral: tinted buttons.
 */
private data class ConnColors(val connect: Color, val connectSolid: Color, val disconnect: Color, val neutral: Color)

@Composable
private fun connColors(): ConnColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) ConnColors(Color(0xFF22C55E), Color(0xFF16A34A), Color(0xFFF87171), Color(0xFF94A3B8))
    else ConnColors(Color(0xFF16A34A), Color(0xFF16A34A), Color(0xFFDC2626), Color(0xFF5F6672))
}

// ── Row state helpers ─────────────────────────────────────────────────────

/** Ticks once a minute so "Try again in 2h 40m" counts down and the cooldown ends on its own. */
@Composable
private fun rememberNow(untilMs: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMs) {
        while (untilMs != null && now < untilMs) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** COOLDOWN whose time has run out behaves like DISCONNECTED (Connect is allowed again). */
@Composable
fun effectiveStatus(peer: PeerInfo): PeerConnectionStatus {
    val now = rememberNow(peer.cooldownUntilMs)
    return if (peer.status == PeerConnectionStatus.COOLDOWN && (peer.cooldownUntilMs ?: 0L) <= now)
        PeerConnectionStatus.DISCONNECTED else peer.status
}

/** "2h 40m", "35m", "less than a minute". */
fun formatRemaining(ms: Long): String {
    val minutes = (ms / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        minutes < 1 -> "less than a minute"
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** Status line under a not-connected friend's name. */
@Composable
fun disconnectedStatusLine(peer: PeerInfo): String {
    val now = rememberNow(peer.cooldownUntilMs)
    return when (effectiveStatus(peer)) {
        PeerConnectionStatus.REQUEST_RECEIVED -> "Wants to connect"
        PeerConnectionStatus.REQUEST_SENT -> "Request sent · waiting"
        PeerConnectionStatus.COOLDOWN -> "Try again in ${formatRemaining((peer.cooldownUntilMs ?: now) - now)}"
        else -> "Disconnected"
    }
}

// ── The button on the right of each friend row ────────────────────────────

/**
 * Icon-only round buttons (design option C "person", soft):
 * Connected        → red tinted   person −      (disconnect, instant + Undo)
 * Disconnected     → solid green  person +      (send a Connect request)
 * Request sent     → grey tinted  clock         (tap to cancel the request)
 * Request received → grey ✕ (decline) + solid green ✓ (accept)
 * Cooldown         → faded clock, disabled; the status line shows the time left
 * Screen readers still hear what each one does (contentDescription).
 */
@Composable
fun ConnectionActionButton(
    peer: PeerInfo,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit,
    onCancelRequest: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = connColors()
    val first = peer.name.trim().substringBefore(' ').ifBlank { "friend" }
    when (effectiveStatus(peer)) {
        PeerConnectionStatus.CONNECTED ->
            RoundIconButton(R.drawable.ic_conn_person_remove, c.disconnect, filled = false,
                description = "Disconnect from $first", modifier = modifier, onClick = onDisconnect)
        PeerConnectionStatus.DISCONNECTED ->
            RoundIconButton(R.drawable.ic_conn_person_add, c.connectSolid, filled = true,
                description = "Connect with $first", modifier = modifier, onClick = onConnect)
        PeerConnectionStatus.REQUEST_SENT ->
            RoundIconButton(R.drawable.ic_conn_clock, c.neutral, filled = false,
                description = "Request sent to $first. Tap to cancel", modifier = modifier, onClick = onCancelRequest)
        PeerConnectionStatus.REQUEST_RECEIVED ->
            Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundIconButton(R.drawable.ic_conn_close, c.neutral, filled = false,
                    description = "Decline request from $first", onClick = onDecline)
                RoundIconButton(R.drawable.ic_conn_check, c.connectSolid, filled = true,
                    description = "Accept request from $first", onClick = onAccept)
            }
        PeerConnectionStatus.COOLDOWN ->
            RoundIconButton(R.drawable.ic_conn_clock, c.neutral, filled = false, enabled = false,
                description = "$first declined. You can ask again later", modifier = modifier, onClick = {})
    }
}

/** 44 dp round button like a call button: solid with a white icon, or tinted with a coloured one. */
@Composable
private fun RoundIconButton(
    @DrawableRes icon: Int,
    tint: Color,
    filled: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (filled) tint else tint.copy(alpha = 0.12f * alpha),
        border = if (filled) null else BorderStroke(1.5.dp, tint.copy(alpha = 0.4f * alpha)),
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null, // the button itself carries the description
                tint = if (filled) Color.White else tint.copy(alpha = alpha),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Friend profile when not connected ─────────────────────────────────────

/** Replaces steps / lock status on the friend's profile when you're not connected. */
@Composable
fun DisconnectedPeerPanel(
    peer: PeerInfo,
    onConnect: () -> Unit,
    onCancelRequest: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = connColors()
    val status = effectiveStatus(peer)
    val first = peer.name.trim().substringBefore(' ').ifBlank { "your friend" }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                peer.name.trim().split(" ").filter { it.isNotEmpty() }.take(2)
                    .joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(peer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = c.disconnect.copy(alpha = 0.10f)) {
            Text(
                "Disconnected",
                color = c.disconnect, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = when (status) {
                PeerConnectionStatus.REQUEST_RECEIVED -> "$first wants to connect. Accept to see each other's steps and activity again."
                PeerConnectionStatus.REQUEST_SENT -> "Request sent. You'll both see each other's activity once $first accepts."
                PeerConnectionStatus.COOLDOWN -> "$first declined your request. ${disconnectedStatusLine(peer)}."
                else -> "You can't see each other's steps or activity. Connect to ask $first to share again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(20.dp))
        ConnectionActionButton(
            peer = peer,
            onDisconnect = {},
            onConnect = onConnect,
            onCancelRequest = onCancelRequest,
            onAccept = onAccept,
            onDecline = onDecline,
        )
    }
}

// ── Requests inbox ────────────────────────────────────────────────────────

@Composable
fun RequestsBadgeButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = connColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = c.connect.copy(alpha = 0.12f),
        modifier = modifier
            .height(44.dp)
            .semantics { contentDescription = "$count connect request${if (count == 1) "" else "s"}" },
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("🔔", fontSize = 16.sp)
            Box(Modifier.clip(CircleShape).background(c.connectSolid).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text("$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RequestsScreen(
    requests: List<IncomingRequest>,
    onAccept: (IncomingRequest) -> Unit,
    onDecline: (IncomingRequest) -> Unit,
    onBack: () -> Unit,
) {
    val c = connColors()
    val now = rememberNow(Long.MAX_VALUE)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 32.dp),
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back") }
            Spacer(Modifier.height(8.dp))
            Text("Requests", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "People who want to see your activity again. Requests expire after 24 hours.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
        if (requests.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No requests right now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(requests, key = { it.id }) { r ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.fromName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Wants to connect · expires in ${formatRemaining(r.expiresAtMs - now)}",
                            fontSize = 13.sp, color = c.neutral,
                        )
                    }
                    ConnectionActionButton(
                        peer = PeerInfo(r.fromId, r.fromName, true, PeerConnectionStatus.REQUEST_RECEIVED, r.id),
                        onDisconnect = {}, onConnect = {}, onCancelRequest = {},
                        onAccept = { onAccept(r) },
                        onDecline = { onDecline(r) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

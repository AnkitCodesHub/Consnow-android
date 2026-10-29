
package com.Consnow.features.steps.presentation

import android.Manifest
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.features.connections.domain.model.ConnectionsState
import com.Consnow.features.connections.domain.model.PeerInfo
import com.Consnow.features.steps.domain.model.StepsState
import com.Consnow.ui.components.LockStatusWidget
import com.Consnow.ui.components.StepCard
//edited on 26/10/2026
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance

@Composable
fun HomeScreen(
    stepsState: StepsState,
    connectionsState: ConnectionsState,
    onPermissionResult: (Boolean) -> Unit,
    onSync: () -> Unit,
    onPeerClick: (PeerInfo) -> Unit,
    onConnectClick: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit, //option to delete account
    ownIsLocked: Boolean = true,
    ownLastUnlockedAtMs: Long = 0L,
    peerStatuses: Map<String, PeerLiveStatus> = emptyMap(),
) {
    val context = LocalContext.current

    // Ask for everything in ONE request. Two separate launch() calls at the same moment
    // make Android drop the second one (reported as denied, no dialog shown), so on a
    // fresh install Activity Recognition was never asked and steps didn't count.
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Steps need Activity Recognition. Notifications are requested too, but a "no"
        // there doesn't stop step counting.
        val activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            results[Manifest.permission.ACTIVITY_RECOGNITION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        onPermissionResult(activityGranted)
    }

    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) onPermissionResult(true)
        else permissionsLauncher.launch(needed.toTypedArray())
    }

    // Coming back from the Settings page: if the user switched Activity Recognition on
    // there, start counting straight away instead of waiting for the next app launch.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentlyGranted by rememberUpdatedState(stepsState.permissionGranted)
    val currentOnPermissionResult by rememberUpdatedState(onPermissionResult)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !currentlyGranted &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                currentOnPermissionResult(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Friends split into online / offline for the list below
    val fp = friendsPalette()
    val (onlinePeers, offlinePeers) = remember(connectionsState.connections, peerStatuses) {
        connectionsState.connections.partition { peerStatuses[it.id]?.isOnline == true }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(
            top = 64.dp,
            bottom = 32.dp
        )
    ) {
        item {
            // Today's step count
            Text(
                text = "Today's Steps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            StepCard(
                stepCount = stepsState.todaySteps,
                isActive = stepsState.isActive
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sync status
            val (statusText, statusColor) = when {
                !stepsState.sensorAvailable ->
                    "Sensor unavailable" to MaterialTheme.colorScheme.error

                !stepsState.permissionGranted ->
                    "Permission required · Tap to allow" to MaterialTheme.colorScheme.error

                stepsState.syncStatus == SyncStatus.SYNCED ->
                    "Synced" to MaterialTheme.colorScheme.primary

                else ->
                    "Waiting to sync" to
                            MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                modifier = Modifier.clickable {
                    if (!stepsState.permissionGranted) {
                        // After a "Don't allow", Android may never show the popup again,
                        // so send the user to this app's settings page to switch it on.
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        onSync()
                    }
                },
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 6.dp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Activity status
            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (stepsState.isActive) {
                        "Walking"
                    } else {
                        "Last moved ${
                            stepsState.lastActiveAt?.let {
                                formatTimeAgo(it)
                            } ?: "Unknown"
                        }"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (stepsState.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Own phone lock status
            LockStatusWidget(
                isLocked = ownIsLocked,
                lastUnlockedAtMs = ownLastUnlockedAtMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Connection code
            if (connectionsState.myConnectionCode.isNotBlank()) {
                Text(
                    text = "Your code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = connectionsState.myConnectionCode,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = "Share this code so someone can see your steps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Friends header with online / offline counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Friends",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = fp.ink
                    )
                    if (connectionsState.connections.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(fp.onlineDot))
                            Text("${onlinePeers.size} online", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fp.body)
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(8.dp).clip(CircleShape).background(fp.offlineDot))
                            Text("${offlinePeers.size} offline", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fp.body)
                        }
                    }
                }

                Surface(
                    onClick = onConnectClick,
                    shape = RoundedCornerShape(14.dp),
                    color = fp.addBg,
                    modifier = Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Add friend" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", color = fp.addFg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Connected peers list
        if (connectionsState.connections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No connections yet.\nTap Add to enter someone's code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (onlinePeers.isNotEmpty()) {
                item { FriendsSectionLabel("Online now") }
                items(items = onlinePeers, key = { peer -> peer.id }, contentType = { "friend" }) { peer ->
                    FriendCard(peer = peer, status = peerStatuses[peer.id], onClick = { onPeerClick(peer) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            if (offlinePeers.isNotEmpty()) {
                item { FriendsSectionLabel("Offline") }
                items(items = offlinePeers, key = { peer -> peer.id }, contentType = { "friend" }) { peer ->
                    FriendCard(peer = peer, status = peerStatuses[peer.id], onClick = { onPeerClick(peer) })
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Sign out + Delete account
        item {
            Spacer(modifier = Modifier.height(32.dp))

            var showDeleteDialog by remember { mutableStateOf(false) }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete Account") },
                    text = { Text("This will permanently delete your account and all your data. This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            onDeleteAccount()
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    }
                )
            }

            TextButton(onClick = onLogout) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = { showDeleteDialog = true }) {
                Text("Delete Account", color = MaterialTheme.colorScheme.error)
            }
        }
    }

}

// ── Friends list (design: "Consnow Peers Online Dot") ───────────────────────
/** Friends list colors — light and dark, following the app theme (system setting). */
private data class FriendsPalette(
    val ink: Color, val body: Color, val muted: Color,
    val card: Color, val boxBg: Color,
    val onlineDot: Color, val onlineText: Color, val onlineHalo: Color,
    val offlineDot: Color, val offlineText: Color,
    val avatars: List<Color>, val offlineAvatar: Color, val checkingDot: Color,
    val addBg: Color, val addFg: Color,
)

private val LightFriends = FriendsPalette(
    ink = Color(0xFF16181D), body = Color(0xFF4A505B), muted = Color(0xFF5F6672),
    card = Color(0xFFFFFFFF), boxBg = Color(0xFFF4F5F2),
    onlineDot = Color(0xFF16A34A), onlineText = Color(0xFF15803D), onlineHalo = Color(0x2E16A34A),
    offlineDot = Color(0xFFDC2626), offlineText = Color(0xFFB91C1C),
    avatars = listOf(Color(0xFFFDE7C8), Color(0xFFD6E4FF), Color(0xFFDCF2E3), Color(0xFFF3DDF7)),
    offlineAvatar = Color(0xFFE6E7E3), checkingDot = Color(0xFF9CA3AF),
    addBg = Color(0xFF16181D), addFg = Color(0xFFFFFFFF),
)

private val DarkFriends = FriendsPalette(
    ink = Color(0xFFF1F5F9), body = Color(0xFFCBD5E1), muted = Color(0xFF94A3B8),
    card = Color(0xFF1E293B), boxBg = Color(0xFF0F172A),
    onlineDot = Color(0xFF22C55E), onlineText = Color(0xFF4ADE80), onlineHalo = Color(0x3322C55E),
    offlineDot = Color(0xFFEF4444), offlineText = Color(0xFFF87171),
    avatars = listOf(Color(0xFF5B4325), Color(0xFF26375E), Color(0xFF1F4A33), Color(0xFF4A2E55)),
    offlineAvatar = Color(0xFF334155), checkingDot = Color(0xFF64748B),
    addBg = Color(0xFFF1F5F9), addFg = Color(0xFF0F172A),
)

@Composable
private fun friendsPalette(): FriendsPalette =
    // Follow whatever theme the app is actually drawing (dark background => dark palette)
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkFriends else LightFriends

@Composable
private fun FriendsSectionLabel(text: String) {
    val fp = friendsPalette()
    Text(
        text = text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = fp.muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun FriendCard(
    peer: PeerInfo,
    status: PeerLiveStatus?,
    onClick: () -> Unit
) {
    val fp = friendsPalette()
    val isOnline = status?.isOnline == true

    val statusLine = when {
        status == null -> "Checking…"
        isOnline -> "Online · now"
        else -> "Offline"
    }
    val initials = remember(peer.name) {
        peer.name.trim().split(" ").filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = fp.card
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar with status dot
                Box(modifier = Modifier.size(48.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOnline) fp.avatars[(peer.id.hashCode() and 0x7fffffff) % fp.avatars.size]
                                else fp.offlineAvatar
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) fp.ink else fp.body
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) fp.onlineHalo else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        status == null -> fp.checkingDot
                                        isOnline -> fp.onlineDot
                                        else -> fp.offlineDot
                                    }
                                )
                                .border(3.dp, fp.card, CircleShape)
                                .semantics { contentDescription = if (isOnline) "Online" else "Offline" }
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(peer.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fp.ink)
                    Text(
                        text = statusLine,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            status == null -> fp.muted
                            isOnline -> fp.onlineText
                            else -> fp.offlineText
                        }
                    )
                }
            }

        }
    }
}


//updated the timnestamp function
private fun formatPeerTimeAgo(isoTimestamp: String): String {
    return try {
        val then = java.time.Instant.parse(isoTimestamp).toEpochMilli()
        val diffMs = System.currentTimeMillis() - then
        if (diffMs < 0) return "just now"
        val minutes = diffMs / 60_000
        val hours = minutes / 60
        when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${hours / 24}d ago"
        }
    } catch (e: Exception) {
        "Unknown"
    }
}
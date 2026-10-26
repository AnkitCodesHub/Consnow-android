
package com.Consnow.features.steps.presentation

import android.Manifest
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
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            onPermissionResult(true)
        }
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
                    "Permission required" to MaterialTheme.colorScheme.error

                stepsState.syncStatus == SyncStatus.SYNCED ->
                    "Synced" to MaterialTheme.colorScheme.primary

                else ->
                    "Waiting to sync" to
                            MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                modifier = Modifier.clickable {
                    onSync()
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

            // Connected peers header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.titleLarge
                )

                TextButton(
                    onClick = onConnectClick
                ) {
                    Text(text = "Add")
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
            items(
                items = connectionsState.connections,
                key = { peer -> peer.id }
            ) { peer ->
                PeerCard(
                    peer = peer,
                    onClick = {
                        onPeerClick(peer)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun PeerCard(
    peer: PeerInfo,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = peer.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (peer.sharingEnabled) "Sharing steps" else "Sharing paused",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatPeerTimeAgo(isoTimestamp: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val then = sdf.parse(isoTimestamp)?.time ?: return "Unknown"
        val now = System.currentTimeMillis()
        val diffMs = now - then
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
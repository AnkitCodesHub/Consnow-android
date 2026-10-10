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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Consnow.core.database.entity.SyncStatus
import com.Consnow.features.connections.domain.model.ConnectionsState
import com.Consnow.features.connections.domain.model.PeerInfo
import com.Consnow.features.steps.domain.model.StepsState
//import java.text.NumberFormat
//import com.Consnow.ui.components.ActivityRipple
import com.Consnow.ui.components.StepCard
import com.Consnow.features.steps.presentation.formatTimeAgo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    stepsState: StepsState,
    connectionsState: ConnectionsState,
    onPermissionResult: (Boolean) -> Unit,
    onSync: () -> Unit,
    onPeerClick: (PeerInfo) -> Unit,
    onConnectClick: () -> Unit,
    onLogout: () -> Unit
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else {
            onPermissionResult(true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 64.dp, bottom = 32.dp)
    ) {
        // Step count display
        item {
            Text(
                text = "Today's Steps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            StepCard(
                stepCount = stepsState.todaySteps,
                isActive = stepsState.isActive
            )

            Spacer(Modifier.height(12.dp))

            // Sync status chip
            val (statusText, statusColor) = when {
                !stepsState.sensorAvailable -> "Sensor unavailable" to MaterialTheme.colorScheme.error
                !stepsState.permissionGranted -> "Permission required" to MaterialTheme.colorScheme.error
                stepsState.syncStatus == SyncStatus.SYNCED -> "Synced" to MaterialTheme.colorScheme.primary
                else -> "Waiting to sync" to MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = statusColor.copy(alpha = 0.12f),
                modifier = Modifier.clickable { onSync() }
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
//            Last Active At
            Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = if (stepsState.isActive) "🚶 Walking"
                    else "Last moved ${stepsState.lastActiveAt?.let { formatTimeAgo(it) } ?: "Unknown"}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (stepsState.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

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

            Spacer(Modifier.height(32.dp))

            // Connected peers section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onConnectClick) {
                    Text("Add")
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (connectionsState.connections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
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
            items(connectionsState.connections) { peer ->
                PeerCard(peer = peer, onClick = { onPeerClick(peer) })
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
            TextButton(onClick = onLogout) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PeerCard(peer: PeerInfo, onClick: () -> Unit) {
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
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = peer.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(16.dp))

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

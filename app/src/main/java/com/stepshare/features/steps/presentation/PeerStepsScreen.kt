
package com.Consnow.features.steps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.ui.components.LockStatusWidget
import com.Consnow.ui.components.StepCard

@Composable
fun PeerStepsScreen(
    state: PeerStepsState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    isLocked: Boolean = true,
    lastUnlockedAtMs: Long = 0L,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Back")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Loading, error, or peer details
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }

            state.error != null -> {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onRefresh
                ) {
                    Text("Retry")
                }
            }

            else -> {
                // Peer step count
                StepCard(
                    stepCount = state.stepCount,
                    isActive = state.isActive,
                    name = state.peerName
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Activity status
                Text(
                    text = if (state.isActive) {
                        "Walking"
                    } else {
                        "Last moved ${
                            state.lastActiveAt?.let {
                                formatTimeAgo(it)
                            } ?: "Unknown"
                        }"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        letterSpacing = 0.5.sp
                    ),
                    color = if (state.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Manual refresh
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.12f
                    ),
                    onClick = onRefresh
                ) {
                    Text(
                        text = "Tap to refresh",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 6.dp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Peer phone lock status
        LockStatusWidget(
            isLocked = isLocked,
            lastUnlockedAtMs = lastUnlockedAtMs,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
    }
}

// Converts an ISO timestamp into a relative time string.
fun formatTimeAgo(isoTimestamp: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss",
            java.util.Locale.US
        ).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        val then = sdf.parse(isoTimestamp)?.time
            ?: return "Unknown"

        val diffMs = System.currentTimeMillis() - then

        if (diffMs < 0) {
            return "just now"
        }

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

// Converts epoch milliseconds into a relative time string.
fun formatTimeAgoFromMillis(timestamp: Long): String {
    if (timestamp <= 0L) {
        return "Unknown"
    }

    val diffMs = System.currentTimeMillis() - timestamp

    if (diffMs < 0) {
        return "just now"
    }

    val minutes = diffMs / 60_000
    val hours = minutes / 60

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}

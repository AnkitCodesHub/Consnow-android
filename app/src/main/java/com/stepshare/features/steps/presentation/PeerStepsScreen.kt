
package com.Consnow.features.steps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlinx.coroutines.launch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.ui.components.LockStatusWidget
import com.Consnow.ui.components.StepCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerStepsScreen(
    state: PeerStepsState,
    onRefresh: suspend () -> Unit, // returns when the refresh has finished
    onBack: () -> Unit,
    isLocked: Boolean = true,
    lastUnlockedAtMs: Long = 0L,
) {
    val scope = rememberCoroutineScope()

    // Pull down anywhere on the screen to refresh (like YouTube / Chrome).
    // The spinner stays until steps and lock status have both reloaded.
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            try {
                onRefresh()
            } finally {
                pullState.endRefresh()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection)
    ) {
        val screenHeight = maxHeight

        // Scroll container is what lets the pull gesture work; the column inside keeps
        // the exact screen height so the existing centred layout doesn't change.
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
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
                        // Calm, friendly empty state — never the raw exception text
                        Text(
                            text = if (state.isOffline) "You're offline" else "Couldn't load",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (state.isOffline) {
                                "Connect to the internet to see ${state.peerName.ifBlank { "your friend" }}'s activity."
                            } else {
                                state.error
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { scope.launch { onRefresh() } }
                        ) {
                            Text("Try again")
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

                        // Offline note (the old "Tap to refresh" button is replaced by pull-to-refresh)
                        if (state.isOffline) {
                            Spacer(modifier = Modifier.height(24.dp))

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.12f
                                ),
                            ) {
                                Text(
                                    text = "You're offline · pull down to retry",
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

        // Refresh spinner that slides down from the top; hidden when idle
        if (pullState.progress > 0f || pullState.isRefreshing) {
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// Converts an ISO timestamp into a relative time string.
fun formatTimeAgo(isoTimestamp: String): String {
    return try {
        val instant = try {
            java.time.Instant.parse(isoTimestamp)
        } catch (_: Exception) {
            return "Unknown"
        }
        val then = instant.toEpochMilli()

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

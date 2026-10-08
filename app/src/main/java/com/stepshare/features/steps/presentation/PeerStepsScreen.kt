////calls get/steps/user/:userId?date=today
////display their name thir steo count for today
////auto refresh every 30 seconds
////
//
//package com.Consnow.features.steps.presentation
//
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.unit.dp
//import com.Consnow.features.steps.domain.model.PeerStepsState
//import java.text.NumberFormat
//import com.Consnow.ui.components.ActivityRipple
//
//@Composable
//fun PeerStepsScreen(
//    state: PeerStepsState,
//    onRefresh: () -> Unit,
//    onBack: () -> Unit
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(horizontal = 24.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        TextButton(
//            onClick = onBack,
//            modifier = Modifier.align(Alignment.Start)
//        ) {
//            Text("← Back")
//        }
//
//        Spacer(Modifier.weight(1f))
//
//        if (state.isLoading) {
//            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
//        } else if (state.error != null) {
//            Text(
//                text = state.error,
//                color = MaterialTheme.colorScheme.error,
//                style = MaterialTheme.typography.bodyLarge
//            )
//            Spacer(Modifier.height(16.dp))
//            OutlinedButton(onClick = onRefresh) { Text("Retry") }
//        } else {
//            Text(
//                text = state.peerName,
//                style = MaterialTheme.typography.headlineMedium,
//                color = MaterialTheme.colorScheme.onBackground
//            )
//
//            Spacer(Modifier.height(8.dp))
//
//            Text(
//                text = "Today's Steps",
//                style = MaterialTheme.typography.labelLarge,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//
//            Spacer(Modifier.height(8.dp))
//
//            ActivityRipple(
//                isActive = state.isActive,
//                color = MaterialTheme.colorScheme.primary
//            ) {
//                Text(
//                    text = NumberFormat.getNumberInstance().format(state.stepCount),
//                    style = MaterialTheme.typography.displayLarge,
//                    color = MaterialTheme.colorScheme.primary
//                )
//            }
//
//            Spacer(Modifier.height(8.dp))
//
//            Text(
//                text = if (state.isActive) "🚶 Walking"
//                else "Last moved ${state.lastActiveAt?.let { formatTimeAgo(it) } ?: "Unknown"}",
//                style = MaterialTheme.typography.bodyMedium,
//                color = if (state.isActive) MaterialTheme.colorScheme.primary
//                else MaterialTheme.colorScheme.onSurfaceVariant
//            )
//
//            Spacer(Modifier.height(24.dp))
//
//            Surface(
//                shape = RoundedCornerShape(20.dp),
//                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
//                onClick = onRefresh
//            ) {
//                Text(
//                    text = "Tap to refresh",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = MaterialTheme.colorScheme.primary,
//                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
//                )
//            }
//        }
//
//        Spacer(Modifier.weight(1f))
//    }
//}
//// Utility function — put in a utils file or inline
////This function takes a timestamp like "2026-10-03T18:30:00" and converts it into a human-friendly format such as "5m ago", "2h ago", or "3d ago".
//fun formatTimeAgo(isoTimestamp: String): String {
//    return try {
////        incoming time format is in iso8601
//        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
////        tells the formatter about timezone
//        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
////        convert timestampt to milliseconds
//        val then = sdf.parse(isoTimestamp)?.time ?: return "Unknown"
////        gets the current time
//        val now = System.currentTimeMillis()
//        val diffMs = now - then
////millsec to min and min to hr
//        val minutes = diffMs / 60_000
//        val hours = minutes / 60
////check what time rnange applies
//        when {
//            minutes < 1 -> "just now"
//            minutes < 60 -> "${minutes}m ago"
//            hours < 24 -> "${hours}h ago"
//            else -> "${hours / 24}d ago"
//        }
//    } catch (e: Exception) {
//        "Unknown"
//    }
//}
package com.Consnow.features.steps.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Consnow.features.steps.domain.model.PeerStepsState
import com.Consnow.ui.components.StepCard

@Composable
fun PeerStepsScreen(
    state: PeerStepsState,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text("← Back")
        }

        Spacer(Modifier.weight(1f))

        if (state.isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRefresh) { Text("Retry") }
        } else {
            // StepCard with peer's name
            StepCard(
                stepCount = state.stepCount,
                isActive = state.isActive,
                name = state.peerName
            )

            Spacer(Modifier.height(16.dp))

            // Activity status text
            Text(
                text = if (state.isActive) "🚶 Walking"
                else "Last moved ${state.lastActiveAt?.let { formatTimeAgo(it) } ?: "Unknown"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                onClick = onRefresh
            ) {
                Text(
                    text = "Tap to refresh",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

fun formatTimeAgo(isoTimestamp: String): String {
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
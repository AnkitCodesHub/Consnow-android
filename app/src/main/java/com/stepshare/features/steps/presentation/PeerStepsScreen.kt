package com.Consnow.features.steps.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Consnow.features.steps.domain.model.PeerStepsState
import java.text.NumberFormat

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
            Text(
                text = state.peerName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Today's Steps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = NumberFormat.getNumberInstance().format(state.stepCount),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
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

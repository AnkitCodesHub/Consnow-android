package com.Consnow.features.connections.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.Consnow.features.connections.domain.model.ConnectionsState

@Composable
fun ConnectScreen(
    state: ConnectionsState,
    onConnect: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
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

        Text(
            text = "Connect",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Enter the 6-character code from the person whose steps you want to see.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it.uppercase() },
            label = { Text("Connection code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (code.length == 6) onConnect(code)
            })
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.connectSuccess) {
            Spacer(Modifier.height(12.dp))
            Text(text = "Connected!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onConnect(code) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = code.length == 6 && !state.isLoading,
            shape = MaterialTheme.shapes.medium
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect")
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

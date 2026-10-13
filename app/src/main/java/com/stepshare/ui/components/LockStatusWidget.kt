package com.Consnow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun LockStatusWidget(
    isLocked: Boolean,
    lastUnlockedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    var timerText by remember { mutableStateOf("") }

    LaunchedEffect(isLocked, lastUnlockedAtMs) {
        when {
            !isLocked -> timerText = "Unlocked"
            lastUnlockedAtMs == 0L -> timerText = "—"
            else -> {
                while (true) {
                    val total = (System.currentTimeMillis() - lastUnlockedAtMs) / 1000
                    timerText = "${total / 60}m ${total % 60}s Ago"
                    delay(1_000)
                }
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 16.dp),
    ) {
        AnimatedLockIcon(
            isLocked = isLocked,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isLocked && lastUnlockedAtMs > 0L) "Last unlocked $timerText"
            else timerText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AnimatedLockIcon(isLocked: Boolean, modifier: Modifier = Modifier) {
    val transition = updateTransition(targetState = isLocked, label = "lock")
    val shackleRotation by transition.animateFloat(
        label = "shackle",
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) },
    ) { locked -> if (locked) 0f else -52f }

    val color = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val sw = size.width * 0.09f
        // Body
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.43f),
            size = Size(size.width * 0.76f, size.height * 0.51f),
            cornerRadius = CornerRadius(size.width * 0.1f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        // Keyhole
        drawCircle(
            color = color,
            radius = size.width * 0.075f,
            center = Offset(size.width / 2f, size.height * 0.67f),
        )
        // Shackle — pivots from left anchor on open
        withTransform({
            rotate(
                degrees = shackleRotation,
                pivot = Offset(size.width * 0.30f, size.height * 0.43f),
            )
        }) {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.21f, size.height * 0.06f),
                size = Size(size.width * 0.58f, size.height * 0.39f),
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
}
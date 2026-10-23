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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LockStatusWidget — shows the peer's current phone lock state.
 *
 * Two states:
 *   - Unlocked → open lock icon + "Unlocked"
 *   - Locked   → closed lock icon + "Last unlocked Xm Ys Ago" (live timer)
 *
 * Offline is handled in PeerStepsViewModel — when network fails,
 * isLocked is set to true, so this widget naturally shows locked.
 *
 * @param isLocked         whether the peer's phone is locked
 * @param lastUnlockedAtMs epoch millis of peer's last unlock, 0 if never unlocked
 */
@Composable
fun LockStatusWidget(
    isLocked: Boolean,
    lastUnlockedAtMs: Long,
    modifier: Modifier = Modifier,
) {
    var timerText by remember { mutableStateOf("") }

    LaunchedEffect(isLocked, lastUnlockedAtMs) {
        when {
            // Phone is unlocked
            !isLocked -> timerText = "Unlocked"

            // Phone is locked, never unlocked before
            lastUnlockedAtMs == 0L -> timerText = "—"

            // Phone is locked — show live "Last unlocked X ago" timer
            else -> {
                while (true) {
                    // NEW -- updated 21 oct
                    val s = ((System.currentTimeMillis() - lastUnlockedAtMs) / 1000).coerceAtLeast(0)
                    timerText = when {
                        s < 60     -> "${s}s ago"
                        s < 3_600  -> "${s / 60}m ago"
                        s < 86_400 -> "${s / 3_600}h ago"
                        else       -> "${s / 86_400}d ago"
                    }
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
        Text( //edited -- 21/oct/2026
            text = if (isLocked && lastUnlockedAtMs > 0L) "Last Unlocked $timerText" else timerText,

            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 0.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Animated lock icon drawn on Canvas.
 * Shackle rotates open (-52°) when unlocked, returns to 0° when locked.
 */
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

        // Lock body rectangle
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.12f, size.height * 0.43f),
            size = Size(size.width * 0.76f, size.height * 0.51f),
            cornerRadius = CornerRadius(size.width * 0.1f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )

        // Keyhole circle in center of body
        drawCircle(
            color = color,
            radius = size.width * 0.075f,
            center = Offset(size.width / 2f, size.height * 0.67f),
        )

        // Shackle arc — pivots from left anchor point when opening
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
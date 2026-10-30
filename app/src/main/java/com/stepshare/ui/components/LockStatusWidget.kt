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
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import kotlin.math.PI
import kotlin.math.cos
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
 * Animated lock icon drawn on Canvas (same design and motion as the iOS app).
 *
 * Solid padlock: onSurface color when locked, primary (teal) when unlocked,
 * keyhole cut out in the background color.
 *
 * Unlock: keyhole turns like a key → shackle pops up → shackle turns 180° around
 *         its right leg (like a real padlock opening), with a soft ring pulse.
 * Lock:   shackle turns back → drops into the body → body "thunks", key turns back.
 * While unlocked, a soft halo gently breathes.
 *
 * Drawn on a 100 × 100 design grid scaled to the canvas size.
 */
@Composable
private fun AnimatedLockIcon(isLocked: Boolean, modifier: Modifier = Modifier) {
    val start = if (isLocked) 0f else 1f          // no animation on first appearance
    val lift = remember { Animatable(start) }      // 0 = seated, 1 = popped up
    val spin = remember { Animatable(start) }      // 0 = closed side, 1 = turned 180°
    val keyTurn = remember { Animatable(start) }   // 0 = upright, 1 = turned like a key
    val squash = remember { Animatable(0f) }       // 1 = body squashed ("thunk")
    val ring = remember { Animatable(1f) }         // 0..1 pulse progress, 1 = invisible
    var shownLocked by remember { mutableStateOf(isLocked) }

    val tint by animateColorAsState(
        targetValue = if (isLocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        animationSpec = tween(400),
        label = "lockTint",
    )
    val hole = MaterialTheme.colorScheme.background

    val breathing = rememberInfiniteTransition(label = "halo")
    val breathe by breathing.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "haloAlpha",
    )

    // A new state cancels a half-finished sequence; animations continue from where they are.
    LaunchedEffect(isLocked) {
        if (shownLocked == isLocked) return@LaunchedEffect
        shownLocked = isLocked
        squash.snapTo(0f)
        launch {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
        }
        if (!isLocked) {
            // 1. key turns  2. shackle pops up  3. shackle turns around its right leg
            launch { keyTurn.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
            delay(100)
            launch { lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 385f)) }
            delay(160)
            spin.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 110f))
        } else {
            // 1. shackle turns back  2. drops in  3. body "thunks", key turns back
            launch { spin.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 195f)) }
            delay(320)
            launch { lift.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 440f)) }
            delay(160)
            launch { keyTurn.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
            squash.animateTo(1f, tween(110, easing = FastOutSlowInEasing))
            squash.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 320f))
        }
    }

    Canvas(modifier = modifier) {
        val u = size.width / 100f

        // Soft halo behind the lock (breathes while unlocked)
        drawCircle(
            color = tint.copy(alpha = if (isLocked) 0.07f else breathe),
            radius = 50f * u,
            center = Offset(50f * u, 60f * u),
        )

        // Ring pulse on every change
        if (ring.value < 1f) {
            drawCircle(
                color = tint.copy(alpha = (1f - ring.value) * 0.7f),
                radius = (40f + 22f * ring.value) * u,
                center = Offset(50f * u, 60f * u),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }

        // Shackle: lifts, then turns around its right leg (a 3D Y-axis turn,
        // drawn as a horizontal flip that passes through edge-on at 90°).
        val turn = cos(spin.value * PI).toFloat()
        withTransform({
            translate(top = -14f * u * lift.value)
            scale(scaleX = turn, scaleY = 1f, pivot = Offset(69f * u, 56f * u))
        }) {
            val shackle = Path().apply {
                moveTo(31f * u, 56f * u)
                lineTo(31f * u, 34f * u)
                arcTo(
                    rect = Rect(31f * u, 15f * u, 69f * u, 53f * u),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                lineTo(69f * u, 56f * u)
            }
            drawPath(shackle, color = tint, style = Stroke(width = 8.5f * u, cap = StrokeCap.Round))
        }

        // Solid body (squashes from the bottom on lock)
        withTransform({
            scale(
                scaleX = 1f + 0.05f * squash.value,
                scaleY = 1f - 0.12f * squash.value,
                pivot = Offset(50f * u, 94f * u),
            )
        }) {
            drawRoundRect(
                color = tint,
                topLeft = Offset(18f * u, 50f * u),
                size = Size(64f * u, 44f * u),
                cornerRadius = CornerRadius(13f * u),
            )
        }

        // Keyhole cut-out, turns like a key
        rotate(degrees = -90f * keyTurn.value, pivot = Offset(50f * u, 72f * u)) {
            drawCircle(color = hole, radius = 6f * u, center = Offset(50f * u, 68f * u))
            drawRoundRect(
                color = hole,
                topLeft = Offset(47.7f * u, 70f * u),
                size = Size(4.6f * u, 11f * u),
                cornerRadius = CornerRadius(2.3f * u),
            )
        }
    }
}

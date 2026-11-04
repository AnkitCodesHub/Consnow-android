package com.Consnow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.Canvas
import com.Consnow.features.steps.presentation.formatTimeAgoFromMillis

/** A friend who opened your profile, and when (epoch millis). */
data class ProfileWatcher(val name: String, val openedAtMs: Long)

const val MAX_WATCHERS_SHOWN = 3
const val WATCHERS_HOLD_MS = 600L

/** Card size: pops in while pressed, pops out while the list is showing. */
fun cardScale(pressed: Boolean, held: Boolean): Float = when {
    held -> 1.04f
    pressed -> 0.96f
    else -> 1f
}

/** A short buzz that works even when the system "touch feedback" setting is off. */
fun vibrateTick(context: Context, ms: Long = 40L) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
        // no vibrator or permission: silently skip
    }
}

/** "Sangita Pandey" -> "Sangita". Only the first word of a name is shown. */
fun firstName(full: String): String = full.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

/** The list shown in the popup: newest first, never more than 3. */
fun visibleWatchers(all: List<ProfileWatcher>): List<ProfileWatcher> =
    all.sortedByDescending { it.openedAtMs }.take(MAX_WATCHERS_SHOWN)

/**
 * Calls [onHold] once the finger has stayed down for [holdMs], then [onRelease] when it lifts
 * (or the gesture is taken over, e.g. by scrolling). Never consumes touches.
 */
fun Modifier.onHoldFor(
    holdMs: Long = WATCHERS_HOLD_MS,
    onRelease: () -> Unit = {},
    onPressChange: (Boolean) -> Unit = {}, // true when the finger goes down, false when it lifts
    onHold: () -> Unit,
): Modifier =
    pointerInput(holdMs) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            onPressChange(true)
            var finished = false // true when the finger lifted or the gesture was taken before the hold time
            withTimeoutOrNull(holdMs) {
                waitForUpOrCancellation()
                finished = true
            }
            if (!finished) {
                onHold()
                waitForUpOrCancellation()
                onRelease()
            }
            onPressChange(false)
        }
    }

@Composable
fun WhosWatchingPopup(
    watchers: List<ProfileWatcher>,
    show: Boolean, // true while the finger is held down
    onGone: () -> Unit, // called after the slide-out has finished
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vibrateTick(context)
    }
    val rows = visibleWatchers(watchers)
    val accent = Color(0xFF2DD4BF)
    val shape = RoundedCornerShape(20.dp)

    // Starts hidden and flips to visible straight away so the card slides in; when the finger
    // lifts it slides out and the popup is removed only after that has finished.
    val visible = remember { MutableTransitionState(false).apply { targetState = show } }
    LaunchedEffect(show) { visible.targetState = show }
    LaunchedEffect(visible.currentState, visible.targetState) {
        if (!visible.currentState && !visible.targetState) onGone()
    }
    val cardWidth = (LocalConfiguration.current.screenWidthDp / 2).dp

    Popup(
        alignment = Alignment.TopStart, // list opens on the left
        offset = IntOffset(x = 24, y = 96),
        properties = PopupProperties(focusable = false), // must not steal the held finger
    ) {
        AnimatedVisibility(
            visibleState = visible,
            // Slides in from and out to the left, growing from its top-left corner
            enter = slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 2 } +
                fadeIn(tween(180)) +
                scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.82f, transformOrigin = TransformOrigin(0f, 0f)),
            exit = slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 2 } +
                fadeOut(tween(140)) +
                scaleOut(tween(180), targetScale = 0.82f, transformOrigin = TransformOrigin(0f, 0f)),
        ) {
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .shadow(16.dp, shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Header is just the eye icon (no heading text)
                    Box(
                        modifier = Modifier.size(30.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { EyeIcon(color = accent, modifier = Modifier.size(18.dp)) }
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    if (rows.isEmpty()) {
                        Text(
                            "No one has looked at your profile yet today.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        )
                    } else {
                        rows.forEach { w ->
                            val name = firstName(w.name)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier.size(30.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(name.take(1).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(10.dp))
                                // Name on top, time under it: the name gets the whole row width
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatTimeAgoFromMillis(w.openedAtMs),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small static eye on the steps card: tells people the card can be held to see who viewed you. */
@Composable
fun WatchersHintBadge(modifier: Modifier = Modifier) {
    EyeIcon(color = Color(0xFF2DD4BF).copy(alpha = 0.45f), modifier = modifier.size(22.dp))
}

@Composable
private fun EyeIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.08f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.02f, w * 0.92f, h * 0.5f)
            quadraticBezierTo(w * 0.5f, h * 0.98f, w * 0.08f, h * 0.5f)
            close()
        }
        drawPath(path, color, style = stroke)
        drawCircle(color, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
    }
}

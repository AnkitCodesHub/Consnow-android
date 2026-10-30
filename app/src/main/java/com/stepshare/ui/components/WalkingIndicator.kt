package com.Consnow.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * "Walking now" pill: an animated walking person + label.
 * Drop-in replacement for a plain "Walking now" text.
 *
 *   if (isWalking) WalkingNowChip()
 */
@Composable
fun WalkingNowChip(
    modifier: Modifier = Modifier,
    label: String = "Walking now",
) {
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalkingFigure(modifier = Modifier.size(34.dp), color = color)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * An animated person walking in place, with knees bending, arms swinging,
 * a slight body bounce and the ground sliding under their feet.
 * Far-side arm and leg are drawn fainter for depth.
 *
 * @param showGround draw the moving ground dashes (turn off at very small sizes)
 */
@Composable
fun WalkingFigure(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    showGround: Boolean = true,
    stepDurationMs: Int = 1000,
    walking: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "walk")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(stepDurationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "walkPhase",
    )

    Canvas(modifier = modifier.semantics { contentDescription = if (walking) "Walking now" else "Standing still" }) {
        drawWalker(if (walking) phase else 0f, color, showGround, walking)
    }
}

/** Draws one frame of the walk cycle on a 100 × 112 design grid. */
private fun DrawScope.drawWalker(t: Float, color: Color, showGround: Boolean, walking: Boolean = true) {
    val s = size.height / 112f
    val ox = size.width / 2f - 50f * s
    fun p(x: Float, y: Float) = Offset(ox + x * s, y * s)

    val w = (2.0 * PI * t).toFloat()
    val bob = if (walking) -3f * (cos(2f * w) + 1f) / 2f else 0f
    val hip = floatArrayOf(50f, 56f + bob)
    val shoulder = floatArrayOf(51.5f, 31f + bob)

    fun leg(ph: Float): List<FloatArray> {
        if (!walking) {
            // Standing: legs straight, slightly apart.
            val th = if (ph == 0f) 0.09f else -0.09f
            val k = floatArrayOf(hip[0] + 22f * sin(th), hip[1] + 22f * cos(th))
            return listOf(hip, k, floatArrayOf(k[0] + 22f * sin(th), k[1] + 22f * cos(th)))
        }
        val a = w + ph
        val thigh = 0.46f * sin(a)
        val knee = 0.12f + 0.95f * max(0f, cos(a)).pow(1.6f)
        val k = floatArrayOf(hip[0] + 22f * sin(thigh), hip[1] + 22f * cos(thigh))
        val shin = thigh - knee
        val foot = floatArrayOf(k[0] + 22f * sin(shin), k[1] + 22f * cos(shin))
        return listOf(hip, k, foot)
    }

    fun arm(ph: Float): List<FloatArray> {
        if (!walking) {
            // Standing: arms relaxed at the sides.
            val u = if (ph == 0f) 0.12f else -0.12f
            val e = floatArrayOf(shoulder[0] + 16f * sin(u), shoulder[1] + 16f * cos(u))
            return listOf(shoulder, e, floatArrayOf(e[0] + 14f * sin(u + 0.12f), e[1] + 14f * cos(u + 0.12f)))
        }
        val a = w + ph
        val upper = -0.5f * sin(a)
        val e = floatArrayOf(shoulder[0] + 16f * sin(upper), shoulder[1] + 16f * cos(upper))
        val fore = upper + 0.55f + 0.25f * max(0f, -sin(a))
        val hand = floatArrayOf(e[0] + 14f * sin(fore), e[1] + 14f * cos(fore))
        return listOf(shoulder, e, hand)
    }

    fun limb(points: List<FloatArray>, alpha: Float, width: Float) {
        val path = Path()
        points.forEachIndexed { i, pt ->
            val o = p(pt[0], pt[1])
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * alpha),
            style = Stroke(width = width * s, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    if (showGround && !walking) {
        // Still ground: one faint line under the feet.
        drawRect(color = color.copy(alpha = 0.25f), topLeft = p(30f, 103f), size = Size(40f * s, 2.2f * s))
    } else if (showGround) {
        val offset = (t * 24f) % 12f
        var x = -12f
        while (x < 112f) {
            val xx = x - offset
            if (xx in 4f..92f) {
                val fade = 1f - abs(xx - 50f) / 48f
                drawRect(
                    color = color.copy(alpha = 0.45f * fade),
                    topLeft = p(xx, 103f),
                    size = Size(6f * s, 2.2f * s),
                )
            }
            x += 12f
        }
    }

    // Far side first (fainter), then body, near side on top.
    limb(arm(PI.toFloat()), 0.45f, 7f)
    limb(leg(PI.toFloat()), 0.45f, 8f)
    limb(listOf(hip, shoulder), 1f, 9f)
    limb(leg(0f), 1f, 8f)
    drawCircle(color = color, radius = 9f * s, center = p(if (walking) 53f else 51f, 17f + bob))
    limb(arm(0f), 1f, 7f)
}

// ─────────────────────────────────────────────────────────────────────────────
// Movement status: big walking animation while moving, "last moved" chip when not.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Replaces the plain "Walking" / "Last moved X ago" text.
 *
 *  - Walking  → big animated walker + "Walking now"
 *  - Not      → chip with a standing figure; wording and color fade with time:
 *      < 2 min          "Just stopped walking"     teal
 *      < 1 h            "Last moved 43m ago"       soft teal
 *      earlier today    "Last moved at 9:10 am"    grey
 *      not today / none "Hasn't moved yet today"   dim grey
 *
 * @param lastActiveAt ISO-8601 timestamp of the last step (as the API returns it)
 */
@Composable
fun MovementStatus(
    isWalking: Boolean,
    lastActiveAt: String?,
    modifier: Modifier = Modifier,
    walkerHeight: Dp = 72.dp,
) {
    // Re-evaluate the wording every 30 s so "43m ago" keeps counting.
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Crossfade(
        targetState = isWalking,
        animationSpec = tween(400),
        modifier = modifier,
        label = "movementStatus",
    ) { walking ->
        if (walking) {
            WalkingBig(walkerHeight)
        } else {
            LastMovedChip(lastMoved = lastMovedInfo(lastActiveAt, now))
        }
    }
}

@Composable
private fun WalkingBig(walkerHeight: Dp) {
    val color = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WalkingFigure(
            modifier = Modifier
                .height(walkerHeight)
                .width(walkerHeight * 1.5f),
            color = color,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Walking now",
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private enum class Recency { JUST_NOW, WITHIN_HOUR, EARLIER_TODAY, NOT_TODAY }

private data class LastMoved(val title: String, val subtitle: String, val recency: Recency)

private fun lastMovedInfo(lastActiveAt: String?, nowMs: Long): LastMoved {
    val then = lastActiveAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?: return LastMoved("Hasn't moved yet today", "no steps today", Recency.NOT_TODAY)

    val zone = ZoneId.systemDefault()
    val thenDate = then.atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val minutes = ((nowMs - then.toEpochMilli()) / 60_000).coerceAtLeast(0)

    if (thenDate.isBefore(today)) {
        val days = java.time.temporal.ChronoUnit.DAYS.between(thenDate, today)
        val sub = if (days == 1L) "last moved yesterday" else "last moved ${days}d ago"
        return LastMoved("Hasn't moved yet today", sub, Recency.NOT_TODAY)
    }
    return when {
        minutes < 2 -> LastMoved("Just stopped walking", "less than 2 min ago", Recency.JUST_NOW)
        minutes < 60 -> LastMoved("Last moved ${minutes}m ago", "within the hour", Recency.WITHIN_HOUR)
        else -> {
            val time = then.atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                .lowercase(Locale.getDefault())
            LastMoved("Last moved at $time", "earlier today", Recency.EARLIER_TODAY)
        }
    }
}

@Composable
private fun LastMovedChip(lastMoved: LastMoved) {
    val teal = MaterialTheme.colorScheme.primary
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    // Every state stays clearly readable: older states get greyer, never faint.
    val color = when (lastMoved.recency) {
        Recency.JUST_NOW -> teal
        Recency.WITHIN_HOUR -> lerp(teal, grey, 0.4f)
        Recency.EARLIER_TODAY -> grey
        Recency.NOT_TODAY -> grey.copy(alpha = 0.85f)
    }

    // Large, high-contrast chip for low-vision readers. Sizes are in sp, so they
    // also grow with the phone's font-size setting; text wraps instead of being cut off.
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .heightIn(min = 72.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(36.dp))
            .padding(start = 14.dp, end = 24.dp, top = 12.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalkingFigure(
            modifier = Modifier.size(48.dp),
            color = color,
            walking = false,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = lastMoved.title,
                color = color,
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = lastMoved.subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
    }
}

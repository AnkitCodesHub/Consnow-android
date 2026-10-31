package com.Consnow.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small "no signal" symbol (wifi arcs with a slash) plus the word "Offline", shown at the top
 * of the friend screen while the friend's phone has no connection.
 */
@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.error
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Friend is offline" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoSignalIcon(color = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Offline",
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Wifi symbol (three arcs and a dot) crossed out by a diagonal line. */
@Composable
private fun NoSignalIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val cx = w / 2f
        val cy = h * 0.82f

        // three arcs, smallest to largest, opening upward
        for (r in listOf(w * 0.20f, w * 0.34f, w * 0.48f)) {
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = stroke,
            )
        }
        drawCircle(color = color, radius = w * 0.07f, center = Offset(cx, cy))

        // the slash that crosses it out
        drawLine(
            color = color,
            start = Offset(w * 0.10f, h * 0.08f),
            end = Offset(w * 0.90f, h * 0.92f),
            strokeWidth = w * 0.10f,
            cap = StrokeCap.Round,
        )
    }
}

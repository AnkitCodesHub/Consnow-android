//Ankit Date: 08/10/2026 Added step card and refined step counting

package com.Consnow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

@Composable
fun StepCard(
    stepCount: Int,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    name: String? = null,           // null = your own card, set for peer
    goalSteps: Int = 10000,         // daily goal for arc progress
    strideMeters: Float = 0.71f     // average stride length
) {
    val progress = (stepCount.toFloat() / goalSteps).coerceIn(0f, 1f)
    val distanceMeters = (stepCount * strideMeters).toInt()
    val distanceText = if (distanceMeters >= 1000) {
        String.format("%.1f km", distanceMeters / 1000f)
    } else {
        "$distanceMeters m"
    }

    // Animated arc progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "arcProgress"
    )

    // Walking emoji bounce animation
    val infiniteTransition = rememberInfiniteTransition(label = "walkBounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isActive) -6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    // Glow pulse when active
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isActive) 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val arcColor = MaterialTheme.colorScheme.primary
    val arcTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Surface(
        modifier = modifier.size(200.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Arc progress
            Canvas(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.Center)
            ) {
                val strokeWidth = 10.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                // Track arc (background)
                drawArc(
                    color = arcTrackColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Progress arc
                drawArc(
                    color = arcColor,
                    startAngle = 150f,
                    sweepAngle = 240f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Glow dot at the end of progress arc when active
                if (isActive && animatedProgress > 0.01f) {
                    val angle = Math.toRadians((150.0 + 240.0 * animatedProgress))
                    val cx = (size.width / 2) + (arcSize.width / 2) * kotlin.math.cos(angle).toFloat()
                    val cy = (size.height / 2) + (arcSize.height / 2) * kotlin.math.sin(angle).toFloat()
                    drawCircle(
                        color = arcColor.copy(alpha = glowAlpha),
                        radius = strokeWidth * 1.5f,
                        center = Offset(cx, cy)
                    )
                }
            }

            // Center content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Name (for peer cards)
                if (name != null) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Walking emoji with bounce
                Text(
                    text = "🚶",  // 🚶
                    fontSize = 24.sp,
                    modifier = Modifier.offset(y = bounceOffset.dp)
                )

                Spacer(Modifier.height(4.dp))

                // Step count
                Text(
                    text = NumberFormat.getNumberInstance().format(stepCount),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Distance
                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
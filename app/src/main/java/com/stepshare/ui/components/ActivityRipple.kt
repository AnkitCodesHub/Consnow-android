//this is creating th eripple effect in the app ui
//Created 03/10/2026 by ANKIT

package com.Consnow.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ActivityRipple(
    isActive: Boolean,
    size: Dp = 140.dp,
    color: Color = Color(0xFF4CAF50),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        if (isActive) {
            val infiniteTransition = rememberInfiniteTransition(label = "ripple")

            // 3 concentric rings, staggered by 800ms each
            repeat(3) { index ->
                val delay = index * 800

                val progress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 2400,
                            delayMillis = delay,
                            easing = LinearOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "ripple_ring_$index"
                )

                Canvas(modifier = Modifier.size(size)) {
                    val radius = (this.size.minDimension / 2) * progress
                    val alpha = (1f - progress).coerceIn(0f, 0.4f)
                    drawCircle(
                        color = color.copy(alpha = alpha),
                        radius = radius
                    )
                }
            }
        }

        // The actual step count content sits on top
        content()
    }
}
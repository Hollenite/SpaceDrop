package com.prototype.airdrop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Single draw pass for orbit guide + optional radar pulse rings. */
@Composable
fun OrbitalBackground(
    modifier: Modifier = Modifier,
    radiusFraction: Float = 0.34f,
    guideColor: Color,
    pulseColor: Color,
    pulseEnabled: Boolean = true,
    showGuide: Boolean = true,
    maxPulseRadiusFraction: Float = 0.45f,
    ringCount: Int = 3,
    pulseDurationMs: Int = 3_000
) {
    val progress = if (pulseEnabled) {
        rememberPulseProgress(pulseDurationMs)
    } else {
        0f
    }

    val strokePx = remember { 1.dp.toPx() }
    val pulseStrokePx = remember { 2.dp.toPx() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) }

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val guideRadius = size.minDimension * radiusFraction

        if (showGuide) {
            drawCircle(
                color = guideColor,
                radius = guideRadius,
                center = center,
                style = Stroke(width = strokePx, pathEffect = dashEffect)
            )
        }

        if (pulseEnabled) {
            val maxRadius = size.minDimension * maxPulseRadiusFraction
            for (index in 0 until ringCount) {
                val phase = ((progress + index.toFloat() / ringCount) % 1f)
                val radius = maxRadius * phase
                val alpha = (1f - phase).coerceIn(0f, 1f) * 0.45f
                drawCircle(
                    color = pulseColor.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = pulseStrokePx)
                )
            }
        }
    }
}

@Composable
private fun rememberPulseProgress(durationMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "orbital_bg")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_pulse"
    )
    return progress
}

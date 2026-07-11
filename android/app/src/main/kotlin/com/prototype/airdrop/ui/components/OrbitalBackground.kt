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
    if (pulseEnabled) {
        AnimatedOrbitalBackground(
            modifier = modifier,
            radiusFraction = radiusFraction,
            guideColor = guideColor,
            pulseColor = pulseColor,
            showGuide = showGuide,
            maxPulseRadiusFraction = maxPulseRadiusFraction,
            ringCount = ringCount,
            pulseDurationMs = pulseDurationMs
        )
    } else {
        StaticOrbitalBackground(
            modifier = modifier,
            radiusFraction = radiusFraction,
            guideColor = guideColor,
            showGuide = showGuide
        )
    }
}

@Composable
private fun StaticOrbitalBackground(
    modifier: Modifier,
    radiusFraction: Float,
    guideColor: Color,
    showGuide: Boolean
) {
    Canvas(modifier = modifier) {
        if (!showGuide) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val guideRadius = size.minDimension * radiusFraction
        drawCircle(
            color = guideColor,
            radius = guideRadius,
            center = center,
            style = Stroke(
                width = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        )
    }
}

@Composable
private fun AnimatedOrbitalBackground(
    modifier: Modifier,
    radiusFraction: Float,
    guideColor: Color,
    pulseColor: Color,
    showGuide: Boolean,
    maxPulseRadiusFraction: Float,
    ringCount: Int,
    pulseDurationMs: Int
) {
    val transition = rememberInfiniteTransition(label = "orbital_bg")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbital_pulse"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val guideRadius = size.minDimension * radiusFraction
        val pulseStrokePx = 2.dp.toPx()

        if (showGuide) {
            drawCircle(
                color = guideColor,
                radius = guideRadius,
                center = center,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            )
        }

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

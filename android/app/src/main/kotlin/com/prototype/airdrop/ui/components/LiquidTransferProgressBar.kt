package com.prototype.airdrop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LiquidTransferProgressBar(
    progressPercent: Int,
    label: String,
    modifier: Modifier = Modifier,
    isComplete: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressPercent / 100f,
        animationSpec = tween(180),
        label = "liquid_progress"
    )

    val fillColor = if (isComplete) Color(0xFF34D399) else MaterialTheme.colorScheme.primary
    val displayLabel = when {
        isComplete -> "Sent ✓"
        progressPercent > 0 -> "$progressPercent%"
        else -> label
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0D1014))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            fillColor.copy(alpha = 0.85f),
                            fillColor,
                            fillColor.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Text(
            text = displayLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

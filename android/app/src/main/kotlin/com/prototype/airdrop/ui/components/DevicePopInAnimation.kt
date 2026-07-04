package com.prototype.airdrop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

@Composable
fun Modifier.devicePopIn(
    visible: Boolean,
    index: Int = 0,
    reduceMotion: Boolean = false
): Modifier {
    val delayMs = if (reduceMotion) 0 else index * 80
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 350, delayMillis = delayMs),
        label = "device_pop_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 350, delayMillis = delayMs),
        label = "device_pop_alpha"
    )
    return this.scale(scale).alpha(alpha)
}

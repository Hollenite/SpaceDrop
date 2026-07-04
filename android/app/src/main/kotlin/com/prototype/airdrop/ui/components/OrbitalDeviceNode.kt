package com.prototype.airdrop.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import kotlin.math.roundToInt

@Composable
internal fun OrbitalDeviceNode(
    ranked: RankedNearbyDevice,
    position: OrbitalPosition,
    widthPx: Float,
    heightPx: Float,
    density: Float,
    scaledRing: Dp,
    opacity: Float,
    popInIndex: Int,
    reduceMotion: Boolean,
    state: AvatarTransferState,
    progress: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (position.offsetXFraction * widthPx * density).roundToInt(),
                    (position.offsetYFraction * heightPx * density).roundToInt()
                )
            }
            .size(scaledRing + 28.dp)
            .devicePopIn(visible = true, index = popInIndex, reduceMotion = reduceMotion),
        contentAlignment = Alignment.Center
    ) {
        AirDropDeviceAvatar(
            deviceName = ranked.device.name,
            state = state,
            progressPercent = progress,
            enabled = enabled,
            onClick = onClick,
            ringSize = scaledRing,
            buttonSize = (scaledRing.value * 0.78f).dp,
            contentAlpha = opacity
        )
    }
}

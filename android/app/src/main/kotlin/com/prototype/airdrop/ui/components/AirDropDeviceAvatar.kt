package com.prototype.airdrop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.prototype.airdrop.ui.theme.SpaceDropColors
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class AvatarTransferState {
    Idle,
    Connecting,
    Sending,
    Sent,
    Failed
}

@Composable
fun AirDropDeviceAvatar(
    deviceName: String,
    state: AvatarTransferState,
    progressPercent: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ringSize: androidx.compose.ui.unit.Dp = 112.dp,
    buttonSize: androidx.compose.ui.unit.Dp = 88.dp,
    contentAlpha: Float = 1f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = when (state) {
            AvatarTransferState.Sending -> progressPercent / 100f
            AvatarTransferState.Sent -> 1f
            else -> 0f
        },
        animationSpec = tween(180),
        label = "avatar_ring_progress"
    )

    val ringColor = when (state) {
        AvatarTransferState.Sent -> SpaceDropColors.Success
        AvatarTransferState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val buttonColor = when (state) {
        AvatarTransferState.Sent -> Color(0xFF1A2E28)
        AvatarTransferState.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> SpaceDropColors.SurfaceElevated
    }

    Column(
        modifier = modifier.alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = com.prototype.airdrop.ui.theme.SpaceDropColors.RingTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke)
                )

                if (animatedProgress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .size(buttonSize)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onClick),
                color = buttonColor,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (state) {
                        AvatarTransferState.Sent -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = SpaceDropColors.Success,
                            modifier = Modifier.size(36.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            if (state == AvatarTransferState.Sending) {
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        Text(
            text = deviceName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.size(width = ringSize, height = 20.dp)
        )

        if (state == AvatarTransferState.Sent) {
            Text(
                text = "Sent",
                style = MaterialTheme.typography.labelSmall,
                color = SpaceDropColors.Success
            )
        } else if (state == AvatarTransferState.Failed) {
            Text(
                text = "Failed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (state == AvatarTransferState.Connecting) {
            Text(
                text = "Connecting…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

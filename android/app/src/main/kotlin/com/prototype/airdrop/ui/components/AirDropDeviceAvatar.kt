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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
    modifier: Modifier = Modifier
) {
    val ringSize = 112.dp
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
        AvatarTransferState.Sent -> Color(0xFF34D399)
        AvatarTransferState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val buttonColor = when (state) {
        AvatarTransferState.Sent -> Color(0xFF16433A)
        AvatarTransferState.Failed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Column(
        modifier = modifier,
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
                    color = Color(0xFF2A313C),
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
                    .size(88.dp)
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
                            tint = Color(0xFF34D399),
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
                color = Color(0xFF34D399)
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

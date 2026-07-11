package com.prototype.airdrop.ui.screens.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.ui.components.AirDropDeviceAvatar
import com.prototype.airdrop.ui.components.AvatarTransferState
import com.prototype.airdrop.ui.theme.SpaceDropColors

/**
 * Hybrid send layout: an AirDrop-style hero for the primary (most-used/best) device, with the
 * remaining devices in a LocalSend-style scrollable list below. Reuses [AirDropDeviceAvatar]'s
 * circular-ring progress for the hero and a compact ring for list rows.
 */
@Composable
fun HybridDevicePicker(
    rankedDevices: List<RankedNearbyDevice>,
    transferProgress: Int,
    avatarStateFor: (deviceId: String, progressPercent: Int) -> AvatarTransferState,
    isDeviceEnabled: (deviceId: String) -> Boolean,
    onDeviceClick: (RankedNearbyDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rankedDevices.isEmpty()) return
    val primary = rankedDevices.firstOrNull { it.isPrimary } ?: rankedDevices.first()
    val secondaries = rankedDevices.filter { it.device.id != primary.device.id }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "hero") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AirDropDeviceAvatar(
                    deviceName = primary.device.name,
                    state = avatarStateFor(primary.device.id, transferProgress),
                    progressPercent = transferProgress,
                    enabled = isDeviceEnabled(primary.device.id),
                    onClick = { onDeviceClick(primary) },
                    ringSize = 168.dp,
                    buttonSize = 132.dp
                )
                Text(
                    text = usageText(primary),
                    style = MaterialTheme.typography.labelMedium,
                    color = usageColor(primary)
                )
                Text(
                    text = proximityText(primary.device.proximityState),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (secondaries.isNotEmpty()) {
            item(key = "others_header") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Other devices",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, bottom = 4.dp)
                )
            }

            items(secondaries, key = { it.device.id }) { ranked ->
                SecondaryDeviceRow(
                    ranked = ranked,
                    state = avatarStateFor(ranked.device.id, transferProgress),
                    progressPercent = transferProgress,
                    enabled = isDeviceEnabled(ranked.device.id),
                    onClick = { onDeviceClick(ranked) }
                )
            }
        }
    }
}

@Composable
private fun SecondaryDeviceRow(
    ranked: RankedNearbyDevice,
    state: AvatarTransferState,
    progressPercent: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpaceDropColors.SurfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (state == AvatarTransferState.Sent) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Computer
                        },
                        contentDescription = null,
                        tint = if (state == AvatarTransferState.Sent) {
                            SpaceDropColors.Success
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ranked.device.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${usageText(ranked)} • ${proximityText(ranked.device.proximityState)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = usageColor(ranked),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(4.dp))

            when (state) {
                AvatarTransferState.Sending -> Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                AvatarTransferState.Connecting -> Text(
                    text = "…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AvatarTransferState.Sent -> Text(
                    text = "Sent",
                    style = MaterialTheme.typography.labelMedium,
                    color = SpaceDropColors.Success
                )
                AvatarTransferState.Failed -> Text(
                    text = "Failed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                AvatarTransferState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun usageColor(ranked: RankedNearbyDevice) =
    if (ranked.timesUsed > 0) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

private fun usageText(ranked: RankedNearbyDevice): String =
    if (ranked.timesUsed > 0) {
        "Used ${ranked.timesUsed} time${if (ranked.timesUsed == 1) "" else "s"}"
    } else {
        "New device"
    }

private fun proximityText(state: ProximityState): String = when (state) {
    ProximityState.READY_NEARBY -> "Right here"
    ProximityState.NEARBY -> "Nearby"
    ProximityState.WEAK_SIGNAL -> "Far"
    ProximityState.OFFLINE -> "Offline"
}

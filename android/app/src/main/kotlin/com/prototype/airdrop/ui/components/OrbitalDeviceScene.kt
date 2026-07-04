package com.prototype.airdrop.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import com.prototype.airdrop.core.model.TransferStatus
import com.prototype.airdrop.core.model.TrustState
import com.prototype.airdrop.ui.theme.SpaceDropColors

enum class OrbitalSceneMode {
    Home,
    Share
}

data class OrbitalNodeVisual(
    val state: AvatarTransferState = AvatarTransferState.Idle,
    val progress: Int = 0,
    val enabled: Boolean = true,
    val opacity: Float = 1f
)

@Composable
fun OrbitalDeviceScene(
    mode: OrbitalSceneMode,
    rankedDevices: List<RankedNearbyDevice>,
    onDeviceClick: (RankedNearbyDevice) -> Unit,
    modifier: Modifier = Modifier,
    /** Home empty state: radar band only — no guide ring, smaller pulse. */
    compact: Boolean = false,
    nodeVisualFor: (RankedNearbyDevice) -> OrbitalNodeVisual = { OrbitalNodeVisual() },
    activeDeviceId: String? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val reduceMotion = remember(context) { MotionPreferences.isReduceMotionEnabled(context) }

    val guideRadius = remember(rankedDevices, mode) {
        when (mode) {
            OrbitalSceneMode.Home -> rankedDevices.firstOrNull()?.device?.confidence?.let {
                OrbitalDeviceLayout.orbitRadiusFraction(it)
            } ?: 0.34f
            OrbitalSceneMode.Share -> 0.34f
        }
    }

    val positions = remember(rankedDevices.size, guideRadius, mode) {
        when (mode) {
            OrbitalSceneMode.Home -> OrbitalDeviceLayout.homePcPositions(rankedDevices.size, guideRadius)
            OrbitalSceneMode.Share -> OrbitalDeviceLayout.sharePositions(rankedDevices.size, guideRadius)
        }
    }

    val showPulse = mode == OrbitalSceneMode.Home && !reduceMotion
    val showGuide = !compact && (rankedDevices.isNotEmpty() || mode == OrbitalSceneMode.Share)

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = maxWidth.value
        val heightPx = maxHeight.value

        OrbitalBackground(
            modifier = Modifier.fillMaxSize(),
            radiusFraction = guideRadius,
            guideColor = SpaceDropColors.OrbitGuide,
            pulseColor = SpaceDropColors.PrimaryMuted,
            pulseEnabled = showPulse,
            showGuide = showGuide,
            maxPulseRadiusFraction = if (compact) 0.32f else 0.45f
        )

        if (mode == OrbitalSceneMode.Home) {
            PhoneHub(modifier = Modifier.align(Alignment.Center))
        }

        rankedDevices.forEachIndexed { index, ranked ->
            key(ranked.device.id) {
                val position = positions.getOrNull(index) ?: return@key
                val isShareCenter = position.isCenter
                val scale = OrbitalDeviceLayout.computeAvatarScale(
                    ranked = ranked,
                    totalDevices = rankedDevices.size,
                    isShareCenter = isShareCenter
                )
                val baseRing = if (isShareCenter) {
                    OrbitalDeviceLayout.primaryRingSize()
                } else {
                    OrbitalDeviceLayout.orbitalRingSize()
                }
                val scaledRing = (baseRing.value * scale * position.avatarScale).dp

                val visual = nodeVisualFor(ranked)
                val opacity = when (mode) {
                    OrbitalSceneMode.Home -> OrbitalDeviceLayout.ringOpacity(ranked.device.confidence)
                    OrbitalSceneMode.Share -> {
                        val isActive = activeDeviceId == null || activeDeviceId == ranked.device.id
                        if (isActive) visual.opacity else 0.4f
                    }
                }

                val enabled = when (mode) {
                    OrbitalSceneMode.Home -> ranked.device.trustState != TrustState.BLOCKED
                    OrbitalSceneMode.Share -> visual.enabled &&
                        visual.state == AvatarTransferState.Idle &&
                        (activeDeviceId == null || activeDeviceId == ranked.device.id)
                }

                OrbitalDeviceNode(
                    ranked = ranked,
                    position = position,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    density = density,
                    scaledRing = scaledRing,
                    opacity = opacity,
                    popInIndex = index,
                    reduceMotion = reduceMotion || compact,
                    state = visual.state,
                    progress = visual.progress,
                    enabled = enabled,
                    onClick = { onDeviceClick(ranked) }
                )
            }
        }
    }
}

@Composable
fun NearbyDevicesRadar(
    rankedDevices: List<RankedNearbyDevice>,
    onDeviceClick: (RankedNearbyDevice) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    OrbitalDeviceScene(
        mode = OrbitalSceneMode.Home,
        rankedDevices = rankedDevices,
        onDeviceClick = onDeviceClick,
        modifier = modifier,
        compact = compact
    )
}

@Composable
fun ShareOrbitalLayout(
    rankedDevices: List<RankedNearbyDevice>,
    activeDeviceId: String?,
    transferTargetId: String?,
    transferStatus: TransferStatus?,
    transferProgress: Int,
    onDeviceClick: (RankedNearbyDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodeVisualFor: (RankedNearbyDevice) -> OrbitalNodeVisual = remember(
        rankedDevices,
        activeDeviceId,
        transferTargetId,
        transferStatus,
        transferProgress
    ) {
        { ranked ->
            ShareOrbitalVisuals.nodeVisual(
                ranked = ranked,
                activeDeviceId = activeDeviceId,
                transferTargetId = transferTargetId,
                transferStatus = transferStatus,
                transferProgress = transferProgress
            )
        }
    }

    OrbitalDeviceScene(
        mode = OrbitalSceneMode.Share,
        rankedDevices = rankedDevices,
        onDeviceClick = onDeviceClick,
        modifier = modifier,
        nodeVisualFor = nodeVisualFor,
        activeDeviceId = activeDeviceId
    )
}

private object ShareOrbitalVisuals {
    fun nodeVisual(
        ranked: RankedNearbyDevice,
        activeDeviceId: String?,
        transferTargetId: String?,
        transferStatus: TransferStatus?,
        transferProgress: Int
    ): OrbitalNodeVisual {
        val deviceId = ranked.device.id
        val isTransferTarget = transferTargetId == deviceId && activeDeviceId == deviceId

        val state = when {
            !isTransferTarget || activeDeviceId == null -> AvatarTransferState.Idle
            transferStatus == TransferStatus.SENDING -> AvatarTransferState.Sending
            transferStatus == TransferStatus.COMPLETED -> AvatarTransferState.Sent
            transferStatus == TransferStatus.FAILED -> AvatarTransferState.Failed
            transferStatus == TransferStatus.CANCELLED -> AvatarTransferState.Idle
            transferStatus == null -> AvatarTransferState.Connecting
            else -> AvatarTransferState.Idle
        }

        val progress = if (isTransferTarget) transferProgress else 0
        val enabled = activeDeviceId == null && state == AvatarTransferState.Idle

        return OrbitalNodeVisual(
            state = state,
            progress = progress,
            enabled = enabled,
            opacity = OrbitalDeviceLayout.ringOpacity(ranked.device.confidence)
        )
    }
}

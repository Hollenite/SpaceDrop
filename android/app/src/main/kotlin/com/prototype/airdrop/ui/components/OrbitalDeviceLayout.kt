package com.prototype.airdrop.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prototype.airdrop.core.discovery.DeviceRanking
import com.prototype.airdrop.core.discovery.RankedNearbyDevice
import kotlin.math.cos
import kotlin.math.sin

enum class OrbitLayoutMode {
    Home,
    Share
}

data class OrbitalPosition(
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val avatarScale: Float,
    val isCenter: Boolean,
    val ringOpacity: Float = 1f
)

object OrbitalDeviceLayout {

    private const val SHARE_PRIMARY_SCALE = 1.18f
    private const val MIN_ORBIT_SCALE = 0.78f

    fun sharePositions(deviceCount: Int, orbitRadiusFraction: Float = 0.34f): List<OrbitalPosition> {
        if (deviceCount <= 0) return emptyList()
        if (deviceCount == 1) {
            return listOf(
                OrbitalPosition(
                    offsetXFraction = 0f,
                    offsetYFraction = 0f,
                    avatarScale = SHARE_PRIMARY_SCALE,
                    isCenter = true
                )
            )
        }

        val result = mutableListOf(
            OrbitalPosition(
                offsetXFraction = 0f,
                offsetYFraction = 0f,
                avatarScale = SHARE_PRIMARY_SCALE,
                isCenter = true
            )
        )

        val orbitCount = deviceCount - 1
        for (index in 0 until orbitCount) {
            val angle = (2.0 * Math.PI * index / orbitCount) - Math.PI / 2
            result.add(
                OrbitalPosition(
                    offsetXFraction = (cos(angle) * orbitRadiusFraction).toFloat(),
                    offsetYFraction = (sin(angle) * orbitRadiusFraction).toFloat(),
                    avatarScale = 0.85f,
                    isCenter = false
                )
            )
        }
        return result
    }

    /** All PCs on ring; most-used (tier 0) at top. No center slot. */
    fun homePcPositions(
        deviceCount: Int,
        orbitRadiusFraction: Float = 0.34f
    ): List<OrbitalPosition> {
        if (deviceCount <= 0) return emptyList()
        return List(deviceCount) { index ->
            val angle = (2.0 * Math.PI * index / deviceCount) - Math.PI / 2
            OrbitalPosition(
                offsetXFraction = (cos(angle) * orbitRadiusFraction).toFloat(),
                offsetYFraction = (sin(angle) * orbitRadiusFraction).toFloat(),
                avatarScale = 1f,
                isCenter = false
            )
        }
    }

    fun orbitRadiusFraction(confidence: Float): Float = when {
        confidence >= 0.7f -> 0.28f
        confidence >= 0.4f -> 0.34f
        else -> 0.40f
    }

    fun ringOpacity(confidence: Float): Float = when {
        confidence >= 0.7f -> 1f
        confidence >= 0.4f -> 0.85f
        else -> 0.65f
    }

    fun computeAvatarScale(
        ranked: RankedNearbyDevice,
        totalDevices: Int,
        isShareCenter: Boolean
    ): Float {
        if (isShareCenter) return SHARE_PRIMARY_SCALE
        val usageScore = DeviceRanking.usageScore(ranked.usageTier, totalDevices)
        val confidence = ranked.device.confidence.coerceIn(0f, 1f)
        return lerp(MIN_ORBIT_SCALE, 1f, usageScore) * lerp(0.85f, 1f, confidence)
    }

    fun primaryRingSize(): Dp = 112.dp
    fun orbitalRingSize(): Dp = 88.dp

    private fun lerp(start: Float, end: Float, fraction: Float): Float =
        start + (end - start) * fraction.coerceIn(0f, 1f)
}

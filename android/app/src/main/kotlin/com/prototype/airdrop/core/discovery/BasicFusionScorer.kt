package com.prototype.airdrop.core.discovery

import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.core.model.TrustState

data class FusionBreakdown(
    val baseConfidence: Float,
    val mdnsBoost: Float,
    val rssi: Int?,
    val hasBle: Boolean,
    val hasMdns: Boolean
)

object BasicFusionScorer {
    private const val MIN_BLE_RSSI = -85
    private const val HIDE_BLE_ONLY_RSSI = -85

    fun score(candidate: MergedDiscoveryCandidate): Pair<NearbyDevice, FusionBreakdown> {
        val rssi = candidate.rssi
        var baseConfidence = 0f
        var proximity = ProximityState.OFFLINE

        if (candidate.hasBle && rssi != null) {
            when {
                rssi >= -65 -> {
                    baseConfidence = 0.9f
                    proximity = ProximityState.READY_NEARBY
                }
                rssi >= -80 -> {
                    baseConfidence = 0.6f
                    proximity = ProximityState.NEARBY
                }
                rssi >= MIN_BLE_RSSI -> {
                    baseConfidence = 0.3f
                    proximity = ProximityState.WEAK_SIGNAL
                }
            }
        } else if (candidate.hasMdns) {
            baseConfidence = 0.5f
            proximity = ProximityState.NEARBY
        } else if (candidate.hasBle) {
            baseConfidence = 0.4f
            proximity = ProximityState.WEAK_SIGNAL
        }

        var mdnsBoost = 0f
        if (candidate.hasBle && candidate.hasMdns) {
            mdnsBoost = 0.2f
        }

        val confidence = (baseConfidence + mdnsBoost).coerceAtMost(1f)
        val host = candidate.host?.takeIf { it.isNotBlank() }

        val device = NearbyDevice(
            id = candidate.deviceId,
            name = candidate.name,
            trustState = TrustState.PAIRED,
            proximityState = proximity,
            isReady = host != null,
            confidence = confidence,
            host = host,
            port = candidate.port.takeIf { it > 0 } ?: SpaceDropHandshakeClient.HANDSHAKE_PORT
        )

        return device to FusionBreakdown(
            baseConfidence = baseConfidence,
            mdnsBoost = mdnsBoost,
            rssi = rssi,
            hasBle = candidate.hasBle,
            hasMdns = candidate.hasMdns
        )
    }

    fun scoreAll(candidates: List<MergedDiscoveryCandidate>): List<Pair<NearbyDevice, FusionBreakdown>> =
        candidates
            .filter { candidate ->
                when {
                    candidate.hasBle && candidate.rssi != null && !candidate.hasMdns ->
                        candidate.rssi >= HIDE_BLE_ONLY_RSSI
                    candidate.hasBle && candidate.rssi == null && !candidate.hasMdns ->
                        false
                    else -> candidate.host != null || candidate.hasMdns || candidate.hasBle
                }
            }
            .map { score(it) }
            .sortedByDescending { it.first.confidence }
}

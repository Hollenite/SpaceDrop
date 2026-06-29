package com.prototype.airdrop.core.discovery

import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient
import com.prototype.airdrop.core.connectivity.WifiNetworkSnapshot
import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.core.model.TrustState

object FullFusionScorer {
    private const val MIN_BLE_RSSI = -85
    private const val HIDE_BLE_ONLY_RSSI = -85

    private const val WEIGHT_MDNS = 0.20f
    private const val WEIGHT_AGREEMENT = 0.10f
    private const val WEIGHT_BSSID = 0.25f
    private const val WEIGHT_HOST = 0.10f

    fun score(
        candidate: MergedDiscoveryCandidate,
        phoneBssid: String?
    ): Pair<NearbyDevice, FusionBreakdown> {
        val rssi = candidate.rssi
        var bleRssiWeight = 0f
        var proximity = ProximityState.OFFLINE

        if (candidate.hasBle && rssi != null) {
            when {
                rssi >= -65 -> {
                    bleRssiWeight = 0.35f
                    proximity = ProximityState.READY_NEARBY
                }
                rssi >= -80 -> {
                    bleRssiWeight = 0.25f
                    proximity = ProximityState.NEARBY
                }
                rssi >= MIN_BLE_RSSI -> {
                    bleRssiWeight = 0.15f
                    proximity = ProximityState.WEAK_SIGNAL
                }
            }
        } else if (candidate.hasMdns) {
            proximity = ProximityState.NEARBY
        } else if (candidate.hasBle) {
            bleRssiWeight = 0.10f
            proximity = ProximityState.WEAK_SIGNAL
        }

        val mdnsWeight = if (candidate.hasMdns) WEIGHT_MDNS else 0f
        val agreementWeight = if (candidate.hasBle && candidate.hasMdns) WEIGHT_AGREEMENT else 0f
        val bssidMatch = WifiNetworkSnapshot.bssidMatches(phoneBssid, candidate.pcBssid)
        val bssidWeight = if (bssidMatch) WEIGHT_BSSID else 0f
        val host = candidate.host?.takeIf { it.isNotBlank() }
        val hostWeight = if (host != null) WEIGHT_HOST else 0f

        if (proximity == ProximityState.OFFLINE && candidate.hasMdns) {
            proximity = ProximityState.NEARBY
        }

        val confidence = (bleRssiWeight + mdnsWeight + agreementWeight + bssidWeight + hostWeight)
            .coerceAtMost(1f)

        val signalSummary = buildString {
            if (candidate.hasBle) append("BLE")
            if (candidate.hasMdns) {
                if (isNotEmpty()) append("+")
                append("mDNS")
            }
            if (bssidMatch) {
                if (isNotEmpty()) append("+")
                append("WiFi")
            }
            if (isEmpty()) append("none")
        }

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
            bleRssiWeight = bleRssiWeight,
            mdnsWeight = mdnsWeight,
            agreementWeight = agreementWeight,
            bssidWeight = bssidWeight,
            hostWeight = hostWeight,
            rssi = rssi,
            hasBle = candidate.hasBle,
            hasMdns = candidate.hasMdns,
            bssidMatch = bssidMatch,
            signalSummary = signalSummary,
            phoneBssid = phoneBssid,
            pcBssid = candidate.pcBssid
        )
    }

    fun scoreAll(
        candidates: List<MergedDiscoveryCandidate>,
        phoneBssid: String?
    ): List<Pair<NearbyDevice, FusionBreakdown>> =
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
            .map { score(it, phoneBssid) }
            .sortedByDescending { it.first.confidence }
}

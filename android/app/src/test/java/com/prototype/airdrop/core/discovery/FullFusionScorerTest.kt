package com.prototype.airdrop.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullFusionScorerTest {

    @Test
    fun mdnsOnly_withoutBssid_hasMdnsAndHostWeights() {
        val candidate = MergedDiscoveryCandidate(
            deviceId = "pc-1",
            name = "Office PC",
            host = "192.168.1.10",
            port = 58392,
            rssi = null,
            hasBle = false,
            hasMdns = true,
            pcBssid = null
        )

        val (device, breakdown) = FullFusionScorer.score(candidate, phoneBssid = null)

        assertEquals(0.30f, device.confidence, 0.001f)
        assertEquals(0.20f, breakdown.mdnsWeight, 0.001f)
        assertEquals(0.10f, breakdown.hostWeight, 0.001f)
        assertEquals(false, breakdown.bssidMatch)
    }

    @Test
    fun mdnsWithMatchingBssid_boostsConfidence() {
        val candidate = MergedDiscoveryCandidate(
            deviceId = "pc-1",
            name = "Office PC",
            host = "192.168.1.10",
            port = 58392,
            rssi = null,
            hasBle = false,
            hasMdns = true,
            pcBssid = "aa:bb:cc:dd:ee:ff"
        )

        val (device, breakdown) = FullFusionScorer.score(candidate, phoneBssid = "aa:bb:cc:dd:ee:ff")

        assertTrue(device.confidence > 0.5f)
        assertEquals(true, breakdown.bssidMatch)
        assertEquals(0.25f, breakdown.bssidWeight, 0.001f)
    }

    @Test
    fun bleAndMdnsAgreement_addsAgreementWeight() {
        val candidate = MergedDiscoveryCandidate(
            deviceId = "pc-1",
            name = "Office PC",
            host = "192.168.1.10",
            port = 58392,
            rssi = -70,
            hasBle = true,
            hasMdns = true,
            pcBssid = "aa:bb:cc:dd:ee:ff"
        )

        val (device, breakdown) = FullFusionScorer.score(candidate, phoneBssid = "aa:bb:cc:dd:ee:ff")

        assertTrue(device.confidence >= 0.8f)
        assertEquals(0.10f, breakdown.agreementWeight, 0.001f)
    }

    @Test
    fun scoreAll_sortsByConfidenceDescending() {
        val weak = MergedDiscoveryCandidate(
            deviceId = "weak",
            name = "Weak",
            host = "10.0.0.2",
            port = 58392,
            rssi = -84,
            hasBle = true,
            hasMdns = false
        )
        val strong = MergedDiscoveryCandidate(
            deviceId = "strong",
            name = "Strong",
            host = "10.0.0.1",
            port = 58392,
            rssi = -60,
            hasBle = true,
            hasMdns = true,
            pcBssid = "aa:bb:cc:dd:ee:ff"
        )

        val scored = FullFusionScorer.scoreAll(listOf(weak, strong), phoneBssid = "aa:bb:cc:dd:ee:ff")

        assertEquals("strong", scored.first().first.id)
        assertTrue(scored.first().first.confidence > scored.last().first.confidence)
    }
}

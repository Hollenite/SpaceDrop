package com.prototype.airdrop.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(0.25f, device.confidence, 0.001f)
        assertEquals(0.15f, breakdown.mdnsWeight, 0.001f)
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

        // mDNS 0.15 + host 0.10 + BSSID 0.20 = 0.45
        assertEquals(0.45f, device.confidence, 0.001f)
        assertEquals(true, breakdown.bssidMatch)
        assertEquals(0.20f, breakdown.bssidWeight, 0.001f)
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

        // BLE(-70) 0.25 + mDNS 0.15 + agreement 0.05 + BSSID 0.20 + host 0.10 = 0.75
        assertEquals(0.75f, device.confidence, 0.001f)
        assertEquals(0.05f, breakdown.agreementWeight, 0.001f)
    }

    @Test
    fun acousticMatch_boostsCorroboratedCandidate() {
        val base = MergedDiscoveryCandidate(
            deviceId = "pc-1",
            name = "Office PC",
            host = "192.168.1.10",
            port = 58392,
            rssi = -60,
            hasBle = true,
            hasMdns = true,
            pcBssid = "aa:bb:cc:dd:ee:ff"
        )

        val (withoutAcoustic, _) = FullFusionScorer.score(base, phoneBssid = "aa:bb:cc:dd:ee:ff")
        val (withAcoustic, breakdown) = FullFusionScorer.score(
            base.copy(acousticTokenMatch = true),
            phoneBssid = "aa:bb:cc:dd:ee:ff"
        )

        assertEquals(0.15f, breakdown.acousticWeight, 0.001f)
        assertTrue(breakdown.acousticMatch)
        assertTrue(withAcoustic.confidence > withoutAcoustic.confidence)
        // Full stack tops out at exactly 1.0
        assertEquals(1.0f, withAcoustic.confidence, 0.001f)
        assertTrue(breakdown.signalSummary.contains("Acoustic"))
    }

    @Test
    fun acousticMatch_neverAppliesWithoutBleOrMdns() {
        // Defensive: a candidate flagged acoustic but with no BLE/mDNS must not be boosted.
        val candidate = MergedDiscoveryCandidate(
            deviceId = "pc-1",
            name = "Office PC",
            host = "192.168.1.10",
            port = 58392,
            rssi = null,
            hasBle = false,
            hasMdns = false,
            pcBssid = null,
            acousticTokenMatch = true
        )

        val (_, breakdown) = FullFusionScorer.score(candidate, phoneBssid = null)

        assertEquals(0f, breakdown.acousticWeight, 0.001f)
        assertFalse(breakdown.acousticMatch)
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

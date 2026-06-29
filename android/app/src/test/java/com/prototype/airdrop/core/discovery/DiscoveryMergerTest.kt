package com.prototype.airdrop.core.discovery



import org.junit.Assert.assertEquals

import org.junit.Assert.assertTrue

import org.junit.Test



class DiscoveryMergerTest {



    @Test

    fun mergesMdnsWithBleByMatchingHostWhenDeviceIdsDiffer() {

        val ble = com.prototype.airdrop.core.connectivity.ble.DiscoveredBlePc(

            deviceId = "ble-id",

            name = "PC",

            host = "192.168.1.10",

            port = 58392,

            rssi = -70

        )

        val mdns = com.prototype.airdrop.core.connectivity.DiscoveredPc(

            serviceName = "ARIYAN-LAP._spacedrop._tcp.local.",

            host = "192.168.1.10",

            port = 58392,

            deviceId = "mdns-id",

            bssid = "aa:bb:cc:dd:ee:ff"

        )



        val merged = DiscoveryMerger.merge(listOf(ble), listOf(mdns))



        assertEquals(1, merged.size)

        val candidate = merged.first()

        assertEquals("ble-id", candidate.deviceId)

        assertTrue(candidate.hasBle)

        assertTrue(candidate.hasMdns)

        assertEquals("aa:bb:cc:dd:ee:ff", candidate.pcBssid)

    }

}



package com.prototype.airdrop.core.discovery



import com.prototype.airdrop.core.connectivity.DiscoveredPc

import com.prototype.airdrop.core.connectivity.SpaceDropHandshakeClient

import com.prototype.airdrop.core.connectivity.ble.DiscoveredBlePc



data class MergedDiscoveryCandidate(

    val deviceId: String,

    val name: String,

    val host: String?,

    val port: Int,

    val rssi: Int?,

    val hasBle: Boolean,

    val hasMdns: Boolean,

    val pcBssid: String? = null,

    /** True when this BLE/mDNS candidate's device-id token was heard acoustically (Step 3). */
    val acousticTokenMatch: Boolean = false

)



object DiscoveryMerger {

    fun merge(

        bleDevices: List<DiscoveredBlePc>,

        mdnsDevices: List<DiscoveredPc>

    ): List<MergedDiscoveryCandidate> {

        val byId = linkedMapOf<String, MergedDiscoveryCandidate>()



        for (ble in bleDevices) {

            byId[ble.deviceId] = MergedDiscoveryCandidate(

                deviceId = ble.deviceId,

                name = ble.name,

                host = ble.host,

                port = ble.port,

                rssi = ble.rssi,

                hasBle = true,

                hasMdns = false

            )

        }



        for (mdns in mdnsDevices) {

            val deviceId = mdns.deviceId ?: fallbackId(mdns)

            val mergeKey = resolveMergeKey(byId, deviceId, mdns)

            val existing = byId[mergeKey]

            if (existing != null) {

                byId[mergeKey] = existing.copy(

                    name = mdns.serviceName.substringBefore('.').ifBlank { existing.name },

                    host = mdns.host.ifBlank { existing.host },

                    port = mdns.port.takeIf { it > 0 } ?: existing.port,

                    hasMdns = true,

                    pcBssid = mdns.bssid ?: existing.pcBssid

                )

            } else {

                byId[deviceId] = MergedDiscoveryCandidate(

                    deviceId = deviceId,

                    name = mdns.serviceName.substringBefore('.').ifBlank { mdns.serviceName },

                    host = mdns.host,

                    port = mdns.port,

                    rssi = null,

                    hasBle = false,

                    hasMdns = true,

                    pcBssid = mdns.bssid

                )

            }

        }



        return byId.values.toList()

    }



    /** Prefer explicit device_id; fall back to BLE entry with the same resolved host. */

    private fun resolveMergeKey(

        byId: Map<String, MergedDiscoveryCandidate>,

        deviceId: String,

        mdns: DiscoveredPc

    ): String {

        if (byId.containsKey(deviceId)) return deviceId

        val host = mdns.host.trim()

        if (host.isNotEmpty()) {

            byId.entries.firstOrNull { (_, candidate) ->

                candidate.hasBle &&

                    candidate.host?.equals(host, ignoreCase = true) == true

            }?.key?.let { return it }

        }

        return deviceId

    }



    private fun fallbackId(mdns: DiscoveredPc): String =

        "pc-${mdns.host}-${mdns.port}"

}



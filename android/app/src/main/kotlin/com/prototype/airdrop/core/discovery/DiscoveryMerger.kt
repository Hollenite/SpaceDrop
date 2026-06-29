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
    val hasMdns: Boolean
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
            val existing = byId[deviceId]
            if (existing != null) {
                byId[deviceId] = existing.copy(
                    name = mdns.serviceName.substringBefore('.').ifBlank { existing.name },
                    host = mdns.host.ifBlank { existing.host },
                    port = mdns.port.takeIf { it > 0 } ?: existing.port,
                    hasMdns = true
                )
            } else {
                byId[deviceId] = MergedDiscoveryCandidate(
                    deviceId = deviceId,
                    name = mdns.serviceName.substringBefore('.').ifBlank { mdns.serviceName },
                    host = mdns.host,
                    port = mdns.port,
                    rssi = null,
                    hasBle = false,
                    hasMdns = true
                )
            }
        }

        return byId.values.toList()
    }

    private fun fallbackId(mdns: DiscoveredPc): String =
        "pc-${mdns.host}-${mdns.port}"
}

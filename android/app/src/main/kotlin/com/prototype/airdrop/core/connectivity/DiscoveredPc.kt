package com.prototype.airdrop.core.connectivity

data class DiscoveredPc(
    val serviceName: String,
    val host: String,
    val port: Int = SpaceDropHandshakeClient.HANDSHAKE_PORT,
    val deviceId: String? = null
)

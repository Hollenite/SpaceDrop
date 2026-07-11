package com.prototype.airdrop.core.connectivity.ble

data class DiscoveredBlePc(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
    val rssi: Int,
    val bluetoothAddress: String = "",
    val lastSeenMs: Long = System.currentTimeMillis()
)

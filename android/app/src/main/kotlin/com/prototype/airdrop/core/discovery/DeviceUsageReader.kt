package com.prototype.airdrop.core.discovery

interface DeviceUsageReader {
    fun getTransferCount(deviceId: String): Int
    fun getLastUsedAt(deviceId: String): Long
}

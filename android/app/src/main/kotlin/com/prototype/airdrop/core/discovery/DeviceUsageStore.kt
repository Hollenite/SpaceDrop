package com.prototype.airdrop.core.discovery

import android.content.Context

class DeviceUsageStore(context: Context) : DeviceUsageReader {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getTransferCount(deviceId: String): Int =
        prefs.getInt(countKey(deviceId), 0)

    override fun getLastUsedAt(deviceId: String): Long =
        prefs.getLong(lastUsedKey(deviceId), 0L)

    fun recordSuccessfulTransfer(deviceId: String) {
        val count = getTransferCount(deviceId) + 1
        prefs.edit()
            .putInt(countKey(deviceId), count)
            .putLong(lastUsedKey(deviceId), System.currentTimeMillis())
            .apply()
    }

    private fun countKey(deviceId: String) = "count_$deviceId"
    private fun lastUsedKey(deviceId: String) = "last_$deviceId"

    companion object {
        private const val PREFS_NAME = "spacedrop_device_usage"
    }
}

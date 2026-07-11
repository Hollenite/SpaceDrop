package com.prototype.airdrop.core.discovery

import com.prototype.airdrop.core.model.NearbyDevice

data class RankedNearbyDevice(
    val device: NearbyDevice,
    /** Share flow: most-used PC placed at center. */
    val isPrimary: Boolean,
    /** 0 = most used, higher = less used. */
    val usageTier: Int,
    val orbitIndex: Int,
    /** Successful transfers to this device; 0 = never used (new). */
    val timesUsed: Int = 0
)

object DeviceRanking {

    fun rank(
        devices: List<NearbyDevice>,
        usageStore: DeviceUsageReader
    ): List<RankedNearbyDevice> {
        if (devices.isEmpty()) return emptyList()

        val sorted = devices.sortedWith(
            compareByDescending<NearbyDevice> { usageStore.getTransferCount(it.id) }
                .thenByDescending { usageStore.getLastUsedAt(it.id) }
                .thenByDescending { it.confidence }
        )

        val primaryId = sorted.first().id
        return sorted.mapIndexed { index, device ->
            RankedNearbyDevice(
                device = device,
                isPrimary = device.id == primaryId,
                usageTier = index,
                orbitIndex = index,
                timesUsed = usageStore.getTransferCount(device.id)
            )
        }
    }

    fun usageScore(usageTier: Int, totalDevices: Int): Float {
        if (totalDevices <= 1) return 1f
        return 1f - (usageTier.toFloat() / (totalDevices - 1).coerceAtLeast(1))
    }
}

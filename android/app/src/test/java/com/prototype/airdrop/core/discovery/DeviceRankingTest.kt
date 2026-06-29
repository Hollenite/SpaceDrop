package com.prototype.airdrop.core.discovery

import com.prototype.airdrop.core.model.NearbyDevice
import com.prototype.airdrop.core.model.ProximityState
import com.prototype.airdrop.core.model.TrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRankingTest {

    private class InMemoryUsageStore : DeviceUsageReader {
        private val counts = mutableMapOf<String, Int>()
        private val lastUsed = mutableMapOf<String, Long>()

        override fun getTransferCount(deviceId: String): Int = counts[deviceId] ?: 0
        override fun getLastUsedAt(deviceId: String): Long = lastUsed[deviceId] ?: 0L

        fun seed(deviceId: String, count: Int, lastUsedAt: Long) {
            counts[deviceId] = count
            lastUsed[deviceId] = lastUsedAt
        }
    }

    private fun device(id: String, confidence: Float) = NearbyDevice(
        id = id,
        name = id,
        trustState = TrustState.PAIRED,
        proximityState = ProximityState.NEARBY,
        isReady = true,
        confidence = confidence,
        host = "10.0.0.1"
    )

    @Test
    fun usageScore_mostUsedIsOne() {
        assertEquals(1f, DeviceRanking.usageScore(usageTier = 0, totalDevices = 3), 0.001f)
        assertEquals(0.5f, DeviceRanking.usageScore(usageTier = 1, totalDevices = 3), 0.001f)
        assertEquals(0f, DeviceRanking.usageScore(usageTier = 2, totalDevices = 3), 0.001f)
    }

    @Test
    fun singleDevice_isPrimary() {
        val usage = InMemoryUsageStore()
        val ranked = DeviceRanking.rank(listOf(device("only", 0.8f)), usage)

        assertEquals(1, ranked.size)
        assertTrue(ranked.first().isPrimary)
    }

    @Test
    fun mostUsedDevice_becomesPrimary() {
        val usage = InMemoryUsageStore()
        usage.seed("often", count = 5, lastUsedAt = 100L)
        usage.seed("rare", count = 1, lastUsedAt = 200L)

        val ranked = DeviceRanking.rank(
            listOf(device("rare", 0.95f), device("often", 0.5f)),
            usage
        )

        assertEquals("often", ranked.first().device.id)
        assertTrue(ranked.first().isPrimary)
    }

    @Test
    fun tieBreakUsesLastUsedThenConfidence() {
        val usage = InMemoryUsageStore()
        usage.seed("a", count = 2, lastUsedAt = 50L)
        usage.seed("b", count = 2, lastUsedAt = 100L)

        val ranked = DeviceRanking.rank(
            listOf(device("a", 0.99f), device("b", 0.40f)),
            usage
        )

        assertEquals("b", ranked.first().device.id)
    }
}

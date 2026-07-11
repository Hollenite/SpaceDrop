package com.spacedrop.tier0.fsk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class AcousticTokenTest {
    @Test
    fun fixedVector() {
        val deviceId = "550e8400-e29b-41d4-a716-446655440000"
        val epoch = 100
        val token = AcousticToken.computeToken20(deviceId, epoch)
        assertEquals(0x5142E, token)
    }

    @Test
    fun crcMatchesToken() {
        val deviceId = "550e8400-e29b-41d4-a716-446655440000"
        val epoch = 100
        val token = AcousticToken.computeToken20(deviceId, epoch)
        val crc = Crc4.compute(token)
        val bits = AcousticToken.buildFrameBits(deviceId, epoch)
        var decoded = 0
        for (bit in bits) {
            decoded = (decoded shl 1) or bit
        }
        assertEquals(token shl 4 or crc, decoded)
    }
}

class Crc4Test {
    @Test
    fun knownToken() {
        assertEquals(0x7, Crc4.compute(0x5142E))
    }
}

class FskRoundTripTest {
    private val deviceIds = listOf(
        "550e8400-e29b-41d4-a716-446655440000",
        "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
        "00000000-0000-0000-0000-000000000001"
    )

    @Test
    fun frameDuration() {
        val encoder = FskEncoder()
        val pcm = encoder.encodeFrame("550e8400-e29b-41d4-a716-446655440000", 100)
        val expected = FskProtocol.samplesForMs(FskProtocol.FRAME_ACTIVE_MS)
        assertEquals(expected, pcm.size)
    }

    @Test
    fun roundTripMultipleDevices() {
        val encoder = FskEncoder()
        val decoder = FskDecoder()
        val gap = encoder.silence(FskProtocol.alignedGapMs())
        for (deviceId in deviceIds) {
            for (epoch in listOf(0, 100, 999_999)) {
                val expectedToken = AcousticToken.computeToken20(deviceId, epoch)
                val frame = encoder.encodeFrame(deviceId, epoch)
                val pcm = ShortArray(gap.size + frame.size)
                gap.copyInto(pcm)
                frame.copyInto(pcm, gap.size)
                val frames = decoder.process(pcm)
                decoder.reset()
                assertEquals(1, frames.size, "device=$deviceId epoch=$epoch")
                assertEquals(expectedToken, frames[0].token20)
                assertTrue(frames[0].crcValid)
            }
        }
    }
}

class GoertzelDetectorTest {
    @Test
    fun detectsSineTone() {
        val sampleRate = FskProtocol.SAMPLE_RATE
        val detector = GoertzelDetector(sampleRate, 19_000.0)
        val block = ShortArray(GoertzelDetector.DEFAULT_BLOCK_SIZE)
        val phaseInc = 2.0 * kotlin.math.PI * 19_000.0 / sampleRate
        var phase = 0.0
        for (i in block.indices) {
            block[i] = (kotlin.math.sin(phase) * 0.4 * Short.MAX_VALUE).toInt().toShort()
            phase += phaseInc
        }
        val mag = detector.magnitude(block)
        assertTrue(mag > 0.01)
    }
}

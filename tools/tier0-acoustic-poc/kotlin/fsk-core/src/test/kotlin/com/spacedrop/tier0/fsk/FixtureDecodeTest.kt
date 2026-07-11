package com.spacedrop.tier0.fsk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Paths

class FixtureDecodeTest {
    private val fixturesRoot: File by lazy {
        val fromModule = Paths.get("..", "..", "fixtures").normalize().toFile()
        require(fromModule.isDirectory) { "Fixtures not found at ${fromModule.absolutePath}" }
        fromModule
    }

    @Test
    fun decodeCleanFixtures() {
        val decoder = FskDecoder()
        val cases = listOf(
            Triple("clean-primary.wav", "550e8400-e29b-41d4-a716-446655440000", 100),
            Triple("clean-secondary.wav", "6ba7b810-9dad-11d1-80b4-00c04fd430c8", 0),
            Triple("clean-rotated-epoch.wav", "550e8400-e29b-41d4-a716-446655440000", 101)
        )
        for ((file, deviceId, epoch) in cases) {
            val wav = File(fixturesRoot, "wav/$file").readBytes()
            val (samples, _) = WavIo.readMonoPcm16(wav)
            val expected = AcousticToken.computeToken20(deviceId, epoch)
            val gap = FskEncoder().silence(FskProtocol.alignedGapMs())
            val pcm = ShortArray(gap.size + samples.size)
            gap.copyInto(pcm)
            samples.copyInto(pcm, gap.size)
            val frames = FskDecoder().process(pcm)
            assertEquals(1, frames.size, file)
            assertEquals(expected, frames[0].token20, file)
            assertTrue(frames[0].crcValid, file)
        }
    }

    @Test
    fun rejectBadCrcFixture() {
        val wav = File(fixturesRoot, "wav/bad-crc.wav").readBytes()
        val (samples, _) = WavIo.readMonoPcm16(wav)
        val frames = FskDecoder().process(samples)
        assertEquals(0, frames.size)
    }
}

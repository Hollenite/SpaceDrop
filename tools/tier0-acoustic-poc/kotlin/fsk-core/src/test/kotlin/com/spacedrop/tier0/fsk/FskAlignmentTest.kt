package com.spacedrop.tier0.fsk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reproduces the live decode-reliability problem: on hardware a frame's preamble onset lands at an
 * arbitrary sub-block phase relative to the decoder's Goertzel block grid. If the decoder locks the
 * data symbol clock only to block granularity (~46 ms), every symbol window is shifted and bits slip
 * (observed live as recurring 0x79E3F plus 1-2 bit near-misses, ~18% success).
 *
 * This test sweeps the sub-block phase and requires a correct decode at every offset.
 */
class FskAlignmentTest {
    private val deviceId = "550e8400-e29b-41d4-a716-446655440000"
    private val epoch = 100

    @Test
    fun decodesAcrossSubBlockPhaseOffsets() {
        val encoder = FskEncoder()
        val expected = AcousticToken.computeToken20(deviceId, epoch)
        val frame = encoder.encodeFrameWithGap(deviceId, epoch, gapMs = FskProtocol.INTER_FRAME_GAP_MS)
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val leadGap = encoder.silence(FskProtocol.alignedGapMs())

        var ok = 0
        val offsets = (0 until blockSize step 128).toList()
        val failed = mutableListOf<Int>()
        for (off in offsets) {
            val stream = ShortArray(leadGap.size + off + frame.size)
            leadGap.copyInto(stream, 0)
            frame.copyInto(stream, leadGap.size + off)
            val frames = FskDecoder().process(stream)
            if (frames.any { it.token20 == expected && it.crcValid }) ok++ else failed += off
        }
        assertEquals(offsets.size, ok, "decode must succeed at every sub-block phase offset; failed=$failed")
    }
}

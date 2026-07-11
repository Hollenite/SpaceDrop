package com.spacedrop.tier0.fsk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class FskLiveLeakageTest {
    private val deviceId = "550e8400-e29b-41d4-a716-446655440000"
    private val epoch = 100

    @Test
    fun preambleDetectorRejectsBit1OnlyTone() {
        val sampleRate = FskProtocol.SAMPLE_RATE
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val detector = PreambleDetector(sampleRate, blockSize = blockSize)
        val block = sineBlock(sampleRate, FskProtocol.BIT1_HZ, blockSize, amplitude = 0.4)
        val result = detector.analyze(block)
        assertFalse(result.isPreamble, "19500 Hz alone must not count as preamble")
    }

    @Test
    fun preambleDetectorAcceptsPurePreamble() {
        val sampleRate = FskProtocol.SAMPLE_RATE
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val detector = PreambleDetector(sampleRate, blockSize = blockSize)
        val block = sineBlock(sampleRate, FskProtocol.PREAMBLE_HZ, blockSize, amplitude = 0.4)
        val result = detector.analyze(block)
        assertTrue(result.isPreamble)
        assertTrue(result.snrDb >= FskProtocol.PREAMBLE_LOCK_SNR_DB)
    }

    @Test
    fun preambleDetectorRejectsLowMagnitudeDataLeakage() {
        val sampleRate = FskProtocol.SAMPLE_RATE
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val detector = PreambleDetector(sampleRate, blockSize = blockSize)
        val block = sineBlock(sampleRate, FskProtocol.BIT1_HZ, blockSize, amplitude = 0.4)
        val result = detector.analyze(block)
        assertFalse(
            result.isPreamble,
            "19500 Hz data leakage must not qualify as preamble"
        )
    }

    @Test
    fun dataSymbolStreamDoesNotLockWhenGapArmed() {
        val encoder = FskEncoder()
        val dataOnlyMs = FskProtocol.FRAME_ACTIVE_MS - FskProtocol.PREAMBLE_MS
        val dataTone = sineBlock(
            FskProtocol.SAMPLE_RATE,
            FskProtocol.BIT1_HZ,
            FskProtocol.samplesForMs(dataOnlyMs),
            amplitude = 0.4
        )
        val gap = encoder.silence(FskProtocol.GAP_QUIET_MS + FskProtocol.INTER_FRAME_GAP_MS)
        val stream = ShortArray(gap.size + dataTone.size)
        gap.copyInto(stream)
        dataTone.copyInto(stream, gap.size)

        val decoder = FskDecoder()
        val frames = decoder.process(stream)
        assertTrue(frames.isEmpty(), "19500 Hz data symbols must not trigger preamble lock")
        assertEquals(0, decoder.crcRejectCount)
    }

    @Test
    fun loopedFrameWithGapDecodes() {
        val encoder = FskEncoder()
        val decoder = FskDecoder()
        val frame = encoder.encodeFrameWithGap(deviceId, epoch, gapMs = FskProtocol.INTER_FRAME_GAP_MS)
        val expected = AcousticToken.computeToken20(deviceId, epoch)

        val silence = encoder.silence(FskProtocol.alignedGapMs())
        val stream = ShortArray(silence.size + frame.size + silence.size + frame.size)
        var offset = 0
        silence.copyInto(stream, offset)
        offset += silence.size
        frame.copyInto(stream, offset)
        offset += frame.size
        silence.copyInto(stream, offset)
        offset += silence.size
        frame.copyInto(stream, offset)

        val frames = decoder.process(stream)
        assertTrue(frames.isNotEmpty(), "Expected at least one valid decode in looped stream")
        assertTrue(frames.any { it.token20 == expected })
    }

    @Test
    fun backToBackLoopedFramesDecode() {
        val encoder = FskEncoder()
        val decoder = FskDecoder()
        val frame = encoder.encodeFrameWithGap(deviceId, epoch, gapMs = FskProtocol.INTER_FRAME_GAP_MS)
        val expected = AcousticToken.computeToken20(deviceId, epoch)
        val stream = ShortArray(frame.size * 3)
        val leadGap = encoder.silence(FskProtocol.alignedGapMs())
        val fullStream = ShortArray(leadGap.size + stream.size)
        leadGap.copyInto(fullStream)
        var offset = leadGap.size
        repeat(3) {
            frame.copyInto(fullStream, offset)
            offset += frame.size
        }

        val frames = decoder.process(fullStream)
        assertEquals(3, frames.size, "Expected three valid decodes from back-to-back looped frames")
        assertTrue(frames.all { it.token20 == expected && it.crcValid })
    }

    @Test
    fun lateQualifyingPreambleStillDecodes() {
        val encoder = FskEncoder()
        val fullFrame = encoder.encodeFrame(deviceId, epoch)
        val expected = AcousticToken.computeToken20(deviceId, epoch)
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val gapMs = FskProtocol.alignedGapMs(FskProtocol.GAP_QUIET_MS + FskProtocol.INTER_FRAME_GAP_MS)
        val gap = encoder.silence(gapMs)

        val stream = ShortArray(gap.size + fullFrame.size)
        gap.copyInto(stream)
        fullFrame.copyInto(stream, gap.size)

        val frames = FskDecoder().process(stream)
        assertEquals(1, frames.size, "Onset lock after aligned gap should decode")
        assertEquals(expected, frames[0].token20)
    }

    /**
     * Regression for the capture-restart bug: a decoder created while the emitter is already
     * transmitting (no leading digital silence) must still decode. Real inter-frame gaps carry
     * faint ambient residual (mic self-noise / reverb), not perfect zeros, so the gap SNR stays
     * above the stale 1e-6 noise floor. The old SNR-only gap gate never armed under these
     * conditions and produced zero decodes despite strong tone presence.
     */
    @Test
    fun restartMidTransmissionWithAmbientGapsDecodes() {
        val encoder = FskEncoder()
        val frame = encoder.encodeFrame(deviceId, epoch)
        val expected = AcousticToken.computeToken20(deviceId, epoch)
        val rng = Random(1234L)

        // Stream STARTS on tone (mid-transmission restart) then loops frame + ambient gap.
        // Pad the gap so (frame + gap) is a whole number of Goertzel blocks: every frame then
        // starts at the same block phase, isolating the arming behavior from symbol-alignment
        // jitter (a synthetic-stream artifact the continuous live decoder does not share).
        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val baseGap = FskProtocol.samplesForMs(FskProtocol.INTER_FRAME_GAP_MS)
        val pad = (blockSize - (frame.size + baseGap) % blockSize) % blockSize
        val gapSamples = baseGap + pad

        val segments = mutableListOf<ShortArray>()
        repeat(4) {
            segments += frame
            segments += ambientGap(gapSamples, rng)
        }
        val stream = concat(segments)

        val frames = FskDecoder().process(stream)
        assertTrue(
            frames.any { it.token20 == expected && it.crcValid },
            "Decoder started mid-transmission with ambient (non-silent) gaps must recover and decode"
        )
    }

    /**
     * Restart landing partway through a frame's data tone (no preamble seen first), followed by
     * ambient gaps and full frames, must still recover on a later frame.
     */
    @Test
    fun restartMidFrameThenRecovers() {
        val encoder = FskEncoder()
        val frame = encoder.encodeFrame(deviceId, epoch)
        val expected = AcousticToken.computeToken20(deviceId, epoch)
        val rng = Random(9876L)

        // Truncated leading tone: skip the preamble, start inside the data symbols.
        val skip = FskProtocol.samplesForMs(FskProtocol.PREAMBLE_MS + 5 * FskProtocol.SYMBOL_MS)
        val partialFrame = frame.copyOfRange(skip, frame.size)

        val blockSize = GoertzelDetector.DEFAULT_BLOCK_SIZE
        val baseGap = FskProtocol.samplesForMs(FskProtocol.INTER_FRAME_GAP_MS)
        val gapSamples = baseGap + (blockSize - (frame.size + baseGap) % blockSize) % blockSize

        val segments = mutableListOf<ShortArray>()
        // Consume the partial frame plus a filler so the first full frame lands block-aligned.
        segments += partialFrame
        segments += ambientGap(
            baseGap + (blockSize - (partialFrame.size + baseGap) % blockSize) % blockSize,
            rng
        )
        repeat(3) {
            segments += frame
            segments += ambientGap(gapSamples, rng)
        }
        val stream = concat(segments)

        val frames = FskDecoder().process(stream)
        assertTrue(
            frames.any { it.token20 == expected && it.crcValid },
            "Decoder starting mid-frame must recover on a subsequent clean frame"
        )
    }

    /** Faint broadband residual: quiet in absolute terms but well above a stale 1e-6 noise floor. */
    private fun ambientGap(count: Int, rng: Random): ShortArray {
        val out = ShortArray(count)
        for (i in 0 until count) {
            out[i] = (rng.nextGaussian() * AMBIENT_NOISE_LSB).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private fun concat(segments: List<ShortArray>): ShortArray {
        val total = segments.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (segment in segments) {
            segment.copyInto(out, offset)
            offset += segment.size
        }
        return out
    }

    private companion object {
        /** ~8 LSB gaussian noise: preamble-bin mag well below the 5e-5 quiet floor yet clearly
         * above ~8 dB SNR vs the stale 1e-6 seed, so it reproduced the old no-arm regression. */
        const val AMBIENT_NOISE_LSB = 8.0
    }

    private fun sineBlock(sampleRate: Int, frequencyHz: Double, count: Int, amplitude: Double): ShortArray {
        val out = ShortArray(count)
        val phaseInc = 2.0 * PI * frequencyHz / sampleRate
        var phase = 0.0
        for (i in 0 until count) {
            out[i] = (sin(phase) * amplitude * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            phase += phaseInc
        }
        return out
    }
}

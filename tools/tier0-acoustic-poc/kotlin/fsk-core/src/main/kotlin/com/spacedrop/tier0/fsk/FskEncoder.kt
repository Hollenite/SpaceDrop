package com.spacedrop.tier0.fsk

import kotlin.math.PI
import kotlin.math.sin

class FskEncoder(
    private val sampleRate: Int = FskProtocol.SAMPLE_RATE,
    private val amplitude: Double = FskProtocol.AMPLITUDE
) {
    fun encodeFrame(deviceId: String, epoch: Int): ShortArray {
        val bits = AcousticToken.buildFrameBits(deviceId, epoch)
        return encodeBits(bits)
    }

    fun encodeFrameWithGap(deviceId: String, epoch: Int, gapMs: Int = FskProtocol.INTER_FRAME_GAP_MS): ShortArray {
        val frame = encodeFrame(deviceId, epoch)
        val gap = silence(gapMs)
        return ShortArray(frame.size + gap.size) { i ->
            if (i < frame.size) frame[i] else gap[i - frame.size]
        }
    }

    fun encodeBits(bits: IntArray): ShortArray {
        require(bits.size == FskProtocol.PAYLOAD_BITS) { "Expected ${FskProtocol.PAYLOAD_BITS} bits" }
        val segments = mutableListOf<ShortArray>()
        segments += sineSegment(FskProtocol.PREAMBLE_HZ, FskProtocol.PREAMBLE_MS)
        for (bit in bits) {
            val freq = if (bit == 1) FskProtocol.BIT1_HZ else FskProtocol.BIT0_HZ
            segments += sineSegment(freq, FskProtocol.SYMBOL_MS)
        }
        val total = segments.sumOf { it.size }
        val out = ShortArray(total)
        var offset = 0
        for (segment in segments) {
            segment.copyInto(out, offset)
            offset += segment.size
        }
        return out
    }

    fun silence(ms: Int): ShortArray = ShortArray(FskProtocol.samplesForMs(ms, sampleRate))

    private fun sineSegment(frequencyHz: Double, durationMs: Int): ShortArray {
        val count = FskProtocol.samplesForMs(durationMs, sampleRate)
        val out = ShortArray(count)
        val phaseInc = 2.0 * PI * frequencyHz / sampleRate
        var phase = 0.0
        for (i in 0 until count) {
            out[i] = (sin(phase) * amplitude * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            phase += phaseInc
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
        return out
    }
}

package com.spacedrop.tier0.fsk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavIo {
    fun writeMonoPcm16(samples: ShortArray, sampleRate: Int = FskProtocol.SAMPLE_RATE): ByteArray {
        val dataSize = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataSize)
        out.write("RIFF".toByteArray())
        out.write(intLe(36 + dataSize))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(intLe(16))
        out.write(shortLe(1))
        out.write(shortLe(1))
        out.write(intLe(sampleRate))
        out.write(intLe(sampleRate * 2))
        out.write(shortLe(2))
        out.write(shortLe(16))
        out.write("data".toByteArray())
        out.write(intLe(dataSize))
        val buffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            buffer.putShort(sample)
        }
        out.write(buffer.array())
        return out.toByteArray()
    }

    fun readMonoPcm16(wavBytes: ByteArray): Pair<ShortArray, Int> {
        require(wavBytes.size >= 44) { "WAV too short" }
        require(String(wavBytes, 0, 4) == "RIFF") { "Not RIFF" }
        require(String(wavBytes, 8, 4) == "WAVE") { "Not WAVE" }
        var offset = 12
        var sampleRate = FskProtocol.SAMPLE_RATE
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, offset, 4)
            val chunkSize = leInt(wavBytes, offset + 4)
            val chunkData = offset + 8
            when (chunkId) {
                "fmt " -> {
                    sampleRate = leInt(wavBytes, chunkData + 4)
                }
                "data" -> {
                    dataOffset = chunkData
                    dataSize = chunkSize
                }
            }
            offset = chunkData + chunkSize + (chunkSize and 1)
        }
        require(dataOffset >= 0) { "No data chunk" }
        val sampleCount = dataSize / 2
        val samples = ShortArray(sampleCount)
        val buffer = ByteBuffer.wrap(wavBytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            samples[i] = buffer.short
        }
        return samples to sampleRate
    }

    private fun intLe(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )

    private fun shortLe(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), (value shr 8 and 0xFF).toByte())

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

package com.spacedrop.tier0.fsk

import java.security.MessageDigest

object AcousticToken {
    fun epochFromUnixSeconds(unixSeconds: Long): Int =
        (unixSeconds / FskProtocol.EPOCH_SECONDS).toInt()

    fun computeToken20(deviceId: String, epoch: Int): Int {
        val idBytes = deviceId.toByteArray(Charsets.UTF_8)
        val epochBytes = byteArrayOf(
            (epoch ushr 24).toByte(),
            (epoch ushr 16).toByte(),
            (epoch ushr 8).toByte(),
            epoch.toByte()
        )
        val hash = MessageDigest.getInstance("SHA-256").digest(idBytes + epochBytes)
        return ((hash[0].toInt() and 0xFF) shl 12) or
            ((hash[1].toInt() and 0xFF) shl 4) or
            ((hash[2].toInt() and 0xFF) ushr 4)
    }

    fun buildFrameBits(deviceId: String, epoch: Int): IntArray {
        val token20 = computeToken20(deviceId, epoch)
        val crc = Crc4.compute(token20)
        val bits = IntArray(FskProtocol.PAYLOAD_BITS)
        for (i in 0 until FskProtocol.TOKEN_BITS) {
            bits[i] = (token20 shr (FskProtocol.TOKEN_BITS - 1 - i)) and 1
        }
        for (i in 0 until FskProtocol.CRC_BITS) {
            bits[FskProtocol.TOKEN_BITS + i] = (crc shr (FskProtocol.CRC_BITS - 1 - i)) and 1
        }
        return bits
    }

    fun tokenHex(token20: Int): String =
        "0x${(token20 and 0xFFFFF).toString(16).uppercase().padStart(5, '0')}"
}

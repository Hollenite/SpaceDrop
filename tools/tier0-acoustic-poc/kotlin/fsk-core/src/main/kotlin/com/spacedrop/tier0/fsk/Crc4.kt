package com.spacedrop.tier0.fsk

object Crc4 {
    private const val POLY = 0x3

    /** CRC-4 (poly 0x3, init 0) over 20 token bits MSB first. */
    fun compute(token20: Int): Int {
        var crc = 0
        for (i in FskProtocol.TOKEN_BITS - 1 downTo 0) {
            val bit = (token20 shr i) and 1
            crc = crc xor bit
            crc = if (crc and 8 != 0) {
                ((crc shl 1) xor POLY) and 0xF
            } else {
                (crc shl 1) and 0xF
            }
        }
        return crc
    }

    fun verify(token20: Int, crc4: Int): Boolean = compute(token20) == (crc4 and 0xF)
}

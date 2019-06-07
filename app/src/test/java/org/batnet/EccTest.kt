package org.batnet

import org.batnet.ecc.*
import org.junit.Test
import org.junit.Assert.*

class `Ecc Test` {
    @Test
    fun `ECC works`() {
        val d1 = 0b00010010111001L.getBytes().flatMap { it.getBits() }.subList(0, ECC_DATA_BITS).reversed()
        val d2 = 0b00000000000001L.getBytes().flatMap { it.getBits() }.subList(0, ECC_DATA_BITS).reversed()
        val d3 = 0b0110_00110100L.getBytes().flatMap { it.getBits() }.subList(0, ECC_DATA_BITS).reversed()
        println(SHIFT_LEFT_DIGITS)
        for (i in 0..7) {
            println("${i.b.getBits(BITS_PER_SYMBOL).joinToString("")} << ${i.shiftLeftModC().b.getBits(ECC_CHECKSUM_SIZE).joinToString("")}")
            println("${i.b.getBits(BITS_PER_SYMBOL).joinToString("")} >> ${i.shiftRightModC().b.getBits(ECC_CHECKSUM_SIZE).joinToString("")}")
            assertEquals(i, i.shiftLeftModC().shiftRightModC())
        }
        assertEquals(d3.getECC(), null)
    }

    @Test
    fun `CRC Polynomial is well-formed`() {
        assertEquals(1, CRC_POLYNOMIAL and 1)
        assertEquals(1, CRC_POLYNOMIAL ushr ECC_CHECKSUM_SIZE)
    }
}


package org.batnet

import org.batnet.BITS_PER_SYMBOL
import org.batnet.CONSTELLATION_EXPONENTS
import org.batnet.RECEIVER_BUFFER_SIZE
import org.batnet.SAMPLES_PER_SYMBOL
import org.junit.Test

class MagicNumbersAssertions {

    @Test
    fun `samples per symbol is a divisor of receiver buffer size`() {
        assert(RECEIVER_BUFFER_SIZE % SAMPLES_PER_SYMBOL == 0)
    }

    /*@Test
    fun `Transmission of trailer is longer then buffer size`() {
        val trailerSizeInBytes = PREAMBLE1.size - TRAILER_START
        val trailerSizeInSymbols = trailerSizeInBytes * 8 / BITS_PER_SYMBOL
        val trailerSizeInSamples = trailerSizeInSymbols * symbolLength

        assert(trailerSizeInSamples >= RECEIVER_BUFFER_SIZE)
    }*/

    @Test
    fun `Constellation has the required number of points`() {
        assert(CONSTELLATION_EXPONENTS.size == 1 shl BITS_PER_SYMBOL)
    }

}
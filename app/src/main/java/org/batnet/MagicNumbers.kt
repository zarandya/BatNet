package org.batnet

import org.batnet.utils.SymbolList
import org.batnet.utils.Complex
import org.batnet.utils.polar
import kotlin.math.PI

const val SAMPLERATE = 48000

const val MAX_RECEIVER_BUFFER_SIZE = 8192
const val MIN_RECEIVER_BUFFER_SIZE = 32
const val RECEIVER_BUFFER_SIZE = 1024
const val SAMPLES_PER_SYMBOL = 128
const val SYMBOL_WINDOW_SIZE = 96
const val BITS_PER_SYMBOL = 3

const val MID_FREQUENCY = 22125.0




/**
 * Constellation
 *
 * Big endian:
 *        000
 *     001   100
 *   101       110
 *     111   010
 *        011
 *
 * Little endian:
 *        000
 *     100   001
 *   101       011
 *     111   010
 *        110
 */
val CONSTELLATION_EXPONENTS = arrayOf(2, 3, -1, -2, 1, 4, 0, -3)
val CONSTELLATION = CONSTELLATION_EXPONENTS.map { polar(1.0, PI * it / 4) }.toTypedArray()
val PREAMBLE_CONSTELLATION = CONSTELLATION_EXPONENTS.map { if (it == 1 || it == -1) polar(1.0, PI * it / 4) else Complex(0.0, 0.0) }.toTypedArray()
val NEXT_CONSTELLATION_INDEX = (0 until 8).map { CONSTELLATION_EXPONENTS.indexOf((CONSTELLATION_EXPONENTS[it] + 10) % 8 - 3).toByte() }.toByteArray()
val PREV_CONSTELLATION_INDEX = (0 until 8).map { CONSTELLATION_EXPONENTS.indexOf((CONSTELLATION_EXPONENTS[it] + 12) % 8 - 3).toByte() }.toByteArray()



val XSYMBOL = CONSTELLATION_EXPONENTS.indexOf(0).b
val ZSYMBOL = CONSTELLATION_EXPONENTS.indexOf(1).b
val OSYMBOL = CONSTELLATION_EXPONENTS.indexOf(-1).b
val PREAMBLE_AXIS_SYNC = listOf(XSYMBOL, XSYMBOL, XSYMBOL, XSYMBOL, XSYMBOL, XSYMBOL, XSYMBOL, XSYMBOL)
//val PREAMBLE_PHASE_SYNC = listOf(ZSYMBOL, OSYMBOL, ZSYMBOL, ZSYMBOL, OSYMBOL, OSYMBOL, ZSYMBOL, OSYMBOL)
val PREAMBLE_PHASE_SYNC = listOf<Byte>(0b100, 0b110, 0b111, 0b101, 0b111, 0b110, 0b001, 0b010)
val PREAMBLE_TRAILER = listOf(XSYMBOL, 0b100, 0b110, 0b111, 0b101, 0b111, 0b110, 0b001, 0b010, 0b110, 0b111, 0b101, 0b001, 0b010)




val PREAMBLE_SENDER_STRING = SymbolList(PREAMBLE_AXIS_SYNC + PREAMBLE_PHASE_SYNC + PREAMBLE_TRAILER)
val PREAMBLE_RECEIVER_BITSTRING = SymbolList(PREAMBLE_PHASE_SYNC).toBitList()
val PREAMBLE_TRAILER_STRING = SymbolList(PREAMBLE_TRAILER)

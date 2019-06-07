package org.batnet.utils

import org.batnet.BITS_PER_SYMBOL
import org.batnet.fromBits
import org.batnet.getBits

/**
 * Identical to [ArrayList]<[Byte]>, but used for storing bits.
 */
class BitList: ArrayList<Byte> {
    constructor(): super()
    constructor(initialCapacity: Int): super(initialCapacity)
    constructor(c: Collection<Byte>): super(c)

    fun toSymbolList() = chunked(BITS_PER_SYMBOL).mapTo(SymbolList()) { it.fromBits() }
    operator fun plusAssign(bytes: ByteArray) {
        bytes.flatMapTo(this) { it.getBits() }
    }
}

fun String.toBitList() = toByteArray().toBitList()
fun ByteArray.toBitList() = flatMapTo(BitList()) { it.getBits() }
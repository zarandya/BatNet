package org.batnet.utils

import org.batnet.BITS_PER_SYMBOL
import org.batnet.b
import org.batnet.ushr
import kotlin.experimental.and

/**
 * Identical to [ArrayList]<[Byte]> but used for storing symbols of [BITS_PER_SYMBOL] bits
 */
class SymbolList: ArrayList<Byte> {

    constructor(): super()
    constructor(initialCapacity: Int): super(initialCapacity)
    constructor(c: Collection<Byte>): super(c)

    fun toBitList() = flatMapTo(BitList()) { symbol -> (0 until BITS_PER_SYMBOL).map { (symbol ushr it) and 1.b }}
}

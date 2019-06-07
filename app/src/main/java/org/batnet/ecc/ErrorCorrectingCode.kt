package org.batnet.ecc

import android.util.Log.d
import org.batnet.*
import org.batnet.utils.*
import org.batnet.utils.*
import kotlin.collections.plusAssign
import kotlin.math.sqrt

private val ROTATE_CLOCKWISE_MULTIPLICATIVE = Complex(sqrt(0.5), -sqrt(0.5))
private val ROTATE_ANTICLOCKWISE_MULTIPLICATIVE = Complex(sqrt(0.5), sqrt(0.5))

fun List<Byte>.getECC(): List<Byte> {
    var r = 0
    for (b in this) {
        if (r and 1 != 0) {
            r = r xor CRC_POLYNOMIAL
        }
        r = r ushr 1 or (b.i shl ECC_CHECKSUM_SIZE)
    }
    for (i in 0 until ECC_CHECKSUM_SIZE + 1) {
        if (r and 1 != 0) {
            r = r xor CRC_POLYNOMIAL
        }
        r = r ushr 1
    }
    return r.b.getBits(ECC_CHECKSUM_SIZE)

}

fun BitList.addECC() =
        chunked(ECC_DATA_BITS)
                .flatMapTo(BitList()) {
                    val padded = it + ByteArray(ECC_DATA_BITS - it.size).toList()
                    padded + padded.getECC()
                }

private inline fun Complex.scoreMetric() = sqrMagnitude() * (1.0 - y * y / x / x)
//private inline fun Complex.scoreMetric() = 1.25 * x * x + y * y

fun MutableList<Complex>.decodeEcc(): Pair<BitList, Complex> = decodeEccBruteForce()

val SHIFT_LEFT_DIGITS = (0 until ECC_CHECKSUM_SIZE).map {
    var i = 1 shl (it + BITS_PER_SYMBOL)
    if (it + BITS_PER_SYMBOL >= ECC_CHECKSUM_SIZE) {
        for (j in (it + BITS_PER_SYMBOL) downTo ECC_CHECKSUM_SIZE) {
            if (i and (1 shl j) != 0) {
                i = i xor (CRC_POLYNOMIAL shl (j - ECC_CHECKSUM_SIZE))
            }
        }
    }
    i
}
val SHIFT_RIGHT_DIGITS = (0 until BITS_PER_SYMBOL).map {
    var i = 1 shl it
    for (j in it until BITS_PER_SYMBOL) {
        if (i and (1 shl j) != 0) {
            i = i xor (CRC_POLYNOMIAL shl j)
        }
    }
    i ushr BITS_PER_SYMBOL
}

fun Int.shiftLeftModC(): Int {
    var i = 0
    for (j in 0 until ECC_CHECKSUM_SIZE) {
        if (and(1 shl j) > 0) {
            i = i xor SHIFT_LEFT_DIGITS[j]
        }
    }
    return i
}

fun Int.shiftRightModC(): Int {
    var i = ushr(BITS_PER_SYMBOL)
    for (j in 0 until BITS_PER_SYMBOL) {
        if (and(1 shl j) != 0) {
            i = i xor SHIFT_RIGHT_DIGITS[j]
        }
    }
    return i
}

internal data class SymbolAndScoreListLink(val previousLink: SymbolAndScoreListLink?, val symbol: Byte, val score: Complex)

private fun SymbolAndScoreListLink.toBitList(): BitList = if (previousLink == null) {
    BitList()
} else {
    val l = previousLink.toBitList()
    l += symbol.getBits(BITS_PER_SYMBOL)
    l
}

private inline fun Int.andCandidates() = arrayOf(this, PREV_CONSTELLATION_INDEX[this].i, NEXT_CONSTELLATION_INDEX[this].i)

private fun MutableList<Complex>.decodeEccBruteForce(): Pair<BitList, Complex> {
    val relevantSublist = subList(0, BLOCK_SIZE_WITH_ECC)
    val mostLikelySymbols = relevantSublist.map { symbol -> CONSTELLATION.indexOfMaxBy { symbol dot it } }

    val best = getMostLikelySequence(relevantSublist, mostLikelySymbols)

    relevantSublist.clear()

    return Pair(BitList(best.toBitList().subList(0, ECC_DATA_BITS)), best.score.normalised())
}

internal fun getMostLikelySequence(relevantSublist: MutableList<Complex>, mostLikelySymbols: List<Int>): SymbolAndScoreListLink {
    val table = Array(BLOCK_SIZE_WITH_ECC) { HashSet<Int>() }
    d("ECCCandidates", "Most likely: ${mostLikelySymbols.map { it.b.getBits(BITS_PER_SYMBOL).joinToString("") }}")
    table[BLOCK_SIZE_WITH_ECC - 1].add(0)
    for (i in BLOCK_SIZE_WITH_ECC - 1 downTo 1) {
        for (j in table[i]) {
            val k = j.shiftLeftModC()
            mostLikelySymbols[i].andCandidates().forEach {
                table[i - 1].add(k xor it)
            }
        }
    }
    val initial = HashMap<Int, ArrayList<SymbolAndScoreListLink>>()
    initial[0] = arrayListOf(SymbolAndScoreListLink(null, 0, zeroComplex))
    val all = table.foldIndexed(initial) { i, r, column ->
        val next = HashMap<Int, ArrayList<SymbolAndScoreListLink>>()
        for ((j, v) in r) {
            for (l in mostLikelySymbols[i].andCandidates()) {
                val m = (j xor l).shiftRightModC()
                if (column.contains(m)) {
                    val targetList = next.getOrPut(m) { ArrayList() }
                    for (s in v) {
                        targetList += SymbolAndScoreListLink(s, l.b, s.score + CONSTELLATION[l] * !relevantSublist[i])
                    }
                }
            }
        }
        next
    }[0]!!

    d("ECCDecode", "found ${all.size} possible sequences")
    /*if (SDK_INT == -1) {
        all.sortedBy { it.score.scoreMetric() }.map {
            val list = it.toBitList()
            list.subList(0, ECC_DATA_BITS).joinToString("") +
                    "c" + list.subList(ECC_DATA_BITS, list.size).joinToString("") + ":" +
                    " ${it.score} #${it.score.scoreMetric()} ||${it.score.sqrMagnitude()} //${it.score.y / it.score.x}"
        }
                .forEach { d("ECCDecode", it) }
    }*/

    val best = all.maxBy { it.score.scoreMetric() }!!
    return best
}


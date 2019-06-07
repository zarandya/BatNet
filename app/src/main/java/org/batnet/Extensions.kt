@file:Suppress("NOTHING_TO_INLINE")

package org.batnet

import org.batnet.utils.Complex
import java.io.EOFException
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.IllegalStateException
import java.lang.Math.sqrt
import kotlin.collections.ArrayList
import kotlin.concurrent.thread
import kotlin.experimental.and
import kotlin.experimental.or

fun Double.square() = this * this

inline infix fun Byte.shl(other: Int): Int = (this.toInt() shl other)
inline infix fun Byte.ushr(other: Int): Byte = (this.toInt() ushr other).toByte()
inline infix fun Short.ushr(other: Int): Short = (this.toInt() ushr other).toShort()

inline fun Long.getBytes() = byteArrayOf(
        (this and 0xff).toByte(),
        (this ushr 8 and 0xff).toByte(),
        (this ushr 16 and 0xff).toByte(),
        (this ushr 24 and 0xff).toByte(),
        (this ushr 32 and 0xff).toByte(),
        (this ushr 40 and 0xff).toByte(),
        (this ushr 48 and 0xff).toByte(),
        (this ushr 56 and 0xff).toByte()
)

inline fun Double.getBytes(divisor: Double = 32768.0) = (this / divisor).toBits().getBytes()
inline fun Complex.getBytes(divisor: Double = 32768.0) = x.getBytes(divisor) + y.getBytes(divisor)

@Deprecated("This is slow af, don't use for io", ReplaceWith("flatMap { byteArrayOf((it and 0xff).toByte(), (it ushr 8).toByte()).asIterable() }.toByteArray()", "kotlin.experimental.and"))
fun ShortArray.toByteArray() = flatMap { byteArrayOf((it and 0xff).toByte(), (it ushr 8).toByte()).asIterable() }.toByteArray()

/**
 * Conversion between byte and bit array, little endian
 */
fun List<Byte>.fromBits(): Byte = reduceIndexed { index, acc, b -> acc or (b shl index).toByte() }

fun Byte.getBits(numDigits: Int = 8) = (0 until numDigits).map { (this ushr it) and 1 }

fun List<Byte>.symbolsToBits() = flatMapTo(ArrayList()) { it.getBits().subList(0, BITS_PER_SYMBOL) }

inline fun <T, R : Comparable<R>> Array<out T>.indexOfMaxBy(selector: (T) -> R): Int {
    if (isEmpty()) return -1
    var maxIndex = 0
    var maxValue = selector(this[0])
    for (i in 1..lastIndex) {
        val e = this[i]
        val v = selector(e)
        if (maxValue < v) {
            maxIndex = i
            maxValue = v
        }
    }
    return maxIndex
}

inline fun <T, R : Comparable<R>> Iterable<T>.indexOfMaxBy(selector: (T) -> R): Int {
    val iterator = iterator()
    if (!iterator.hasNext()) return -1
    var i = 0
    var maxElem = 0
    var maxValue = selector(iterator.next())
    while (iterator.hasNext()) {
        ++i
        val e = iterator.next()
        val v = selector(e)
        if (maxValue < v) {
            maxElem = i
            maxValue = v
        }
    }
    return maxElem
}


inline infix fun Int.circmod(denom: Int) = (this + denom) % denom

fun <T> List<T>.findSublist(sl: List<T>): Int {
    for (i in 0 until size - sl.size + 1) {
        val it = sl.iterator()
        var j = 0
        while (it.hasNext() && this[i + j] == it.next()) {
            ++j
        }
        if (j == sl.size) return i
    }
    return -1
}

inline fun <E> MutableCollection<E>.removeIfAnySdk(crossinline filter: (E) -> Boolean): Boolean {
    var removed = false
    val each = iterator()
    while (each.hasNext()) {
        if (filter(each.next())) {
            each.remove()
            removed = true
        }
    }
    return removed
}

inline operator fun <T> Array<T>.get(i: Byte) = get(i.toInt())
inline operator fun ByteArray.get(i: Byte) = get(i.toInt())
inline operator fun <T> List<T>.get(i: Byte) = get(i.toInt())
inline operator fun ShortArray.set(i: Long, value: Short) = set(i.i, value)


inline val Int.b get() = toByte()
inline val Int.s get() = toShort()
inline val Int.l get() = toLong()
inline val Int.d get() = toDouble()
inline val Byte.i get() = toInt()
inline val Byte.l get() = toLong()
inline val Long.i get() = toInt()
inline val Short.i get() = toInt()
inline val Short.d get() = toDouble()
inline val Double.b get() = toByte()
inline val Double.s get() = toShort()
inline val Double.i get() = toInt()
inline val Double.d get() = toDouble()


inline fun <reified T> File.forEachObject(action: (T) -> Unit) {
    try {
        ObjectInputStream(inputStream()).use {
            // Will throw EOFException
            while (true) {
                action(it.readObject() as T)
            }
        }
    } catch (_: EOFException) {
    }
}

inline fun <reified T> File.readObjects(): ArrayList<T> {
    val result = ArrayList<T>()
    forEachObject<T> { result += it }
    return result
}

fun <T : Serializable> File.writeObjects(objects: ArrayList<T>) {
    ObjectOutputStream(outputStream()).use { stream ->
        objects.forEach {
            stream.writeObject(it)
        }
    }
}

inline fun <T> initNotOnUIThread(crossinline setter: (T) -> Unit, crossinline initialiser: () -> T) {
    try {
        setter(initialiser())
    } catch (_: IllegalStateException) {
        thread {
            setter(initialiser())
        }
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun Any.wait() = (this as Object).wait()

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun Any.notify() = (this as Object).notify()

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun Any.notifyAll() = (this as Object).notifyAll()

fun List<Double>.stddev(): Double {
    val avg = average()
    return sqrt(sumByDouble { (it - avg).square() } / size)
}

fun intOfBytes(b0: Byte, b1: Byte, b2: Byte, b3: Byte) =
        (b0.i and 0xff) or
                (b1.i shl 8 and 0xff00) or
                (b2.i shl 16 and 0xff0000) or
                (b3.i shl 24)

fun longOfBytes(b: List<Byte>, offset: Int) =
        (b[offset + 0].l and 0xff) or
                (b[offset + 1].l shl 8 and 0xff00) or
                (b[offset + 2].l shl 16 and 0xff0000) or
                (b[offset + 3].l shl 24 and 0xff000000) or
                (b[offset + 4].l shl 32 and 0xff00000000) or
                (b[offset + 5].l shl 40 and 0xff0000000000) or
                (b[offset + 6].l shl 48 and 0xff000000000000)

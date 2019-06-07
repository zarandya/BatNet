package org.batnet.utils

class OffsetArray<T>(val normalSize: Int, val offset: Int) {
    val data = ArrayList<T>(normalSize + offset)

    operator fun get(i: Int) = data[offset + i]
    operator fun set(i: Int, value: T) { data[offset + i] = value }

    fun moveEndingToOffset() {
        for (i in 0 until offset) {
            data[i] = data[normalSize + i]
        }
    }
}
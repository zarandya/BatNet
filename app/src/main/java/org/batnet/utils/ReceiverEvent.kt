package org.batnet.utils

import org.batnet.d
import org.batnet.intOfBytes
import java.io.OutputStream
import java.nio.ByteBuffer

sealed class ReceiverEvent

object PauseRecording : ReceiverEvent()

object StartRecording : ReceiverEvent()

data class FrequencyChange(val newFrequency: Double) : ReceiverEvent()

data class DistanceChange(val cm: Int) : ReceiverEvent()

data class SymbolLengthChange(val samplesPerSymbol: Int, val windowSize: Int): ReceiverEvent()

fun OutputStream.writeEvent(it: ReceiverEvent) {
    when (it) {
        is FrequencyChange -> {
            write(0x00)
            write(0x80)
            write(0xfb)
            write(0xff)
            val b = ByteArray(8)
            ByteBuffer.wrap(b).putDouble(it.newFrequency)
            write(b)
        }
        is DistanceChange -> {
            write(0x00)
            write(0x80)
            write(0xfd)
            write(0xff)
            write(it.cm and 0xff)
            write((it.cm ushr 8) and 0xff)
        }
        is SymbolLengthChange -> {
            write(0x00)
            write(0x80)
            write(0xfc)
            write(0xff)
            write(it.samplesPerSymbol and 0xff)
            write((it.samplesPerSymbol ushr 8) and 0xff)
            write(it.windowSize and 0xff)
            write((it.windowSize ushr 8) and 0xff)
        }
    }
}

fun ByteArray.readEvent(): Pair<ReceiverEvent?, Int> {
    val id = intOfBytes(this[2], this[3], 0, 0)
    return when (id) {
        0xfffe -> Pair(FrequencyChange(intOfBytes(this[4], this[5], 0, 0).d * 500.0), 6) // legacy
        0xfffd -> Pair(DistanceChange(intOfBytes(this[4], this[5], 0, 0)), 6)
        0xfffc -> Pair(SymbolLengthChange(intOfBytes(this[4], this[5], 0, 0), intOfBytes(this[6], this[7], 0, 0)), 8)
        0xfffb -> Pair(FrequencyChange(ByteBuffer.wrap(this, 4, 8).double), 12)
        else -> Pair(null, 0)
    }
}
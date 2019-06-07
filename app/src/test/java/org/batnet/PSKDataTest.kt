package org.batnet

import android.util.Log.i
import org.junit.Test
import org.batnet.receiver.PhaseShiftKeyingSignalProcessor
import org.batnet.services.ChannelLock
import org.batnet.transmitter.PhaseShiftKeyingSignalGenerator
import org.batnet.utils.BufferProducerConsumer
import org.batnet.utils.FrequencyChange
import org.batnet.utils.*
import java.io.*
import java.lang.Thread.sleep
import kotlin.experimental.or


class PSKDataTest: TestUsingNativeLibraries() {

    @Test
    fun main() {


        val ins = FileInputStream("sampledata/c.pcm")
        val rout = FileOutputStream("sampledata/r_test.pcm")
        val sout = FileOutputStream("sampledata/s_test.pcm")
        val lout = FileOutputStream("sampledata/label_test.txt")
        val sumout = FileOutputStream("sampledata/sum_test.pcm")
        val sumcorrout = FileOutputStream("sampledata/sum_corr_test.pcm")
        val axisGuessOut = FileOutputStream("sampledata/axis_guess_test.pcm")

        val insBytes = ByteArray(RECEIVER_BUFFER_SIZE * 2)

        val producerConsumer = BufferProducerConsumer<ShortArray, FrequencyChange> { ShortArray(RECEIVER_BUFFER_SIZE) }


        val psk = PhaseShiftKeyingSignalProcessor(
                producerConsumer,
                ChannelLock(),
                { i("MESSAGE", it) },
                lout = lout,
                rout = rout,
                sout = sout,
                sumout = sumout,
                aout = axisGuessOut,
                sumcorrout = sumcorrout
        )
        psk.carrierFrequency = 18000.0

        while (ins.read(insBytes) != -1) {
            producerConsumer.produce { buffer ->
                insBytes.asIterable().chunked(2).forEachIndexed { index, list ->
                    buffer[index] = list[0].toShort() or (list[1] shl 8).toShort()
                }
            }
        }

        while (producerConsumer.hasNext()) {}

        sleep(100)  // make sure last iteration of symbol extraction is finished so files can be closed
        psk.close()
    }

    @Test
    fun generatePerfectTestData() {

        val signal = PhaseShiftKeyingSignalGenerator().generateSignal2("Hello, world!")

        val cout = FileOutputStream("sampledata/cp.pcm")

        cout.write((signal + (0 until RECEIVER_BUFFER_SIZE).map { 0.toShort() }).toByteArray())
        cout.close()
    }

}


package org.batnet

import android.util.Log.i
import org.junit.Test
import org.batnet.receiver.PhaseShiftKeyingSignalProcessor
import org.batnet.services.ChannelLock
import org.batnet.utils.BufferProducerConsumer
import org.batnet.utils.FrequencyChange
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Thread.sleep
import kotlin.experimental.or

class CalibrationTest: TestUsingNativeLibraries() {
    /*@Test
    fun `generate calibration signal`() {
        val calib = SenderCalibration()
        val signal = calib.generateSenderCalibrationSignal()

        val out = FileOutputStream("sampledata/calib-generated.pcm")
        out.write(signal.toByteArray())
        out.close()
    }*/

    @Test
    fun `receiver calibration`() {
        val pf = "4-n3"
        val ins = FileInputStream("sampledata/calib-prot-$pf.pcm")
        val lout = FileOutputStream("sampledata/label_calib_$pf.txt")
        val sumout = FileOutputStream("sampledata/sum_kt_$pf.pcm")

        val SAMPLERATE_FACTOR = 1

        val insBytes = ByteArray(RECEIVER_BUFFER_SIZE * 2 * SAMPLERATE_FACTOR)

        val producerConsumer = BufferProducerConsumer<ShortArray, FrequencyChange> { ShortArray(RECEIVER_BUFFER_SIZE * SAMPLERATE_FACTOR) }


        val psk = PhaseShiftKeyingSignalProcessor(
                producerConsumer,
                ChannelLock(),
                { i("MESSAGE", it) },
                lout = lout,
                sumout = sumout
        )
        psk.isListeningForCalibration = true

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
}
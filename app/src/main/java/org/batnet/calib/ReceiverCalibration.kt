package org.batnet.calib

import android.util.Log.d
import org.batnet.SAMPLERATE
import java.io.OutputStream
import java.lang.System.loadLibrary

class ReceiverCalibration(
        val symbolWindowSize: Int,
        val receiverBufferSize: Int,
        val lout: OutputStream? = null,
        val calibrationCallback: (HashMap<Int, Double>) -> Unit
) {

    init {
        d("CALIB", "populate symbolWindowSize=$symbolWindowSize")
    }

    private val samplesPerFrequency = symbolWindowSize * 5
    val calibBaseFrequency = SAMPLERATE.toDouble() / symbolWindowSize
    val calibStartIndex = (18000/calibBaseFrequency).toInt()
    val calibEndIndex = symbolWindowSize / 2

    external fun addBuffer(samples: ShortArray, offset: Int): Boolean
    external fun getResults(): DoubleArray

    external fun getAmplitude(samples: ShortArray, offset: Int, frequency: Double): Double

    var nextFrequencyIndex = 0
    var offset = -receiverBufferSize

    fun extractCalibrationSymbols(buffer: ShortArray, t: Int) {
        val signalDone = addBuffer(buffer, offset)
        offset = 0

        if (signalDone) {
            val results = getResults()
            val calibResult = HashMap<Int, Double>()
            results.forEachIndexed { index, score ->
                d("Calib", "%3f: %e".format(calibBaseFrequency * (index + calibStartIndex), score))
                calibResult[index + calibStartIndex] = score
            }
            offset = -receiverBufferSize
            calibrationCallback(calibResult)
        }
        /*
        do {
            val f = nextFrequencyIndex * calibBaseFrequency
            val amplitude = getAmplitude(buffer, offset, f)
            if (amplitude >= 0) {
                val str = "%3f: %e".format(f, amplitude)
                calibResult[nextFrequencyIndex] = amplitude
                d("CalibTest", str)
                offset += samplesPerFrequency
                lout?.write(("${(t + offset - samplesPerFrequency).d / SAMPLERATE}\t" +
                        "${(t + offset).d / SAMPLERATE}\t" +
                        "$str\n").toByteArray())
                ++nextFrequencyIndex
                if (nextFrequencyIndex >= calibEndIndex) {
                    offset = -receiverBufferSize
                    calibrationCallback(HashMap(calibResult))
                    calibResult.clear()
                    return
                }
            }
        } while (amplitude >= 0)
        offset -= receiverBufferSize

         */
    }

    companion object {
        init {
            loadLibrary("receiver-calibration")
        }
    }

    private external fun nativeInit(baseFrequency: Double, startIndex: Int, endIndex: Int, windowSize: Int, samplerate: Int, receiverBufferSize: Int): Long
    @Suppress("unused")
    private val nativePointer = nativeInit(calibBaseFrequency, calibStartIndex, calibEndIndex, samplesPerFrequency, SAMPLERATE, receiverBufferSize)

    @Suppress("ProtectedInFinal", "unused")
    protected external fun finalize()

    private external fun updateNativeStruct(baseFrequency: Double, startIndex: Int, endIndex: Int, windowSize: Int, samplerate: Int, receiverBufferSize: Int)

}
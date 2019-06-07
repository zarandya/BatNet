package org.batnet.receiver

import android.util.Log.d
import org.batnet.*
import org.batnet.calib.ID_BEACON
import org.batnet.calib.ID_BEACON_RESPONSE
import org.batnet.calib.ReceiverCalibration
import org.batnet.ecc.*
import org.batnet.calib.*
import org.batnet.ecc.*
import org.batnet.services.ChannelLock
import org.batnet.utils.*
import org.batnet.utils.*
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.System.loadLibrary
import java.util.*
import kotlin.concurrent.thread
import kotlin.experimental.and
import kotlin.math.PI
import kotlin.math.abs
import kotlin.text.Charsets.UTF_8

class PhaseShiftKeyingSignalProcessor(
        private val producerConsumer: BufferProducerConsumer<ShortArray, out ReceiverEvent>,
        private val lock: ChannelLock,
        private val messageReceiveCallback: (String) -> Unit,

        // TODO do this properly
        private val beaconResponseCallback: ((IntArray) -> Unit)? = null,
        private val chatMessageReceiveCallback: ((ChatMessage) -> Unit)? = null,

        // arguments used for testing and eval. While running on the device, their values are always 0
        private val lout: OutputStream? = null,
        private val rout: FileOutputStream? = null,
        private val sout: FileOutputStream? = null,
        private val sumout: FileOutputStream? = null,
        private val aout: FileOutputStream? = null,
        private val sumcorrout: FileOutputStream? = null,

        private val calibrationCallback: ((Map<Int, Double>) -> Unit)? = null,
        private val symbolsCallback: ((List<Complex>) -> Unit)? = null
) {

    var carrierFrequency = MID_FREQUENCY
        set(value) {
            field = value
            nativeSetCarrierFrequency(value)
        }
    var symbolLength = SAMPLES_PER_SYMBOL
        private set
    var symbolWindowSize = SYMBOL_WINDOW_SIZE
        private set
    private val preambleSize = PREAMBLE_PHASE_SYNC.size
    private var receiverBufferSize  = symbolLength * preambleSize


    private val OMEGA get() = 2.0 * PI * carrierFrequency / SAMPLERATE
    private val BUFFER_LENGTH_ROTATION get() = polar(1.0, OMEGA * receiverBufferSize)

    // global variables updated by symbol extraction and used elsewhere. The native implementation must also update these
    private var prevSymbolBoundary = 0
    private var carrierPhase = oneComplex
    private var selectSymbols = Array<Complex?>(receiverBufferSize / symbolLength + 1) { null }

    // variables used for symbol processing
    private val message = ArrayList<Byte>()
    private val bitsReceivedPreamble = LinkedList<Byte>()
    private val bitsReceived = LinkedList<Byte>()
    private val symbolsReceived = LinkedList<Complex>()

    var isReceivingSignal = false
        private set
    private var receivedEndOfPreamble = 0
    private var carrierPhaseAdjustment = oneComplex
    private var signalStrength = 50.0
    private var signalEndedCounter = 0

    // Try calibration instead of receiving messages
    private fun createRecvCalib() = ReceiverCalibration(symbolWindowSize, receiverBufferSize, lout) {
        calibrationCallback?.invoke(it)
        finishReceivingSignal(false)
        isListeningForCalibration = false
    }
    var isListeningForCalibration = false
    var recvCalib = createRecvCalib()
        private set

    // used only for eval
    var distanceCm = 20

    // Testing private variables
    private var aoutWritten = 0
    private var t = 0

    private fun startRecording() {
        nativeStartRecording()
        t = 0
    }

    private fun pauseRecording() {
        nativePauseRecording()
    }

    init {
        thread(name = "Signal processing thread") {
            while (true) {
                val buffer = producerConsumer.consume { when (it) {
                    StartRecording -> startRecording()
                    PauseRecording -> pauseRecording()
                    is FrequencyChange -> carrierFrequency = it.newFrequency
                    is DistanceChange -> distanceCm = it.cm
                    is SymbolLengthChange -> {
                        symbolLength = it.samplesPerSymbol
                        symbolWindowSize = it.windowSize
                        receiverBufferSize = symbolLength * preambleSize
                        nativeChangeSymbolLength(symbolLength, symbolWindowSize)
                        d("PSK", "symbolWindowSize=$symbolWindowSize")
                        recvCalib = createRecvCalib()
                    }
                } }

                if ((!isListeningForCalibration) || (recvCalib.offset == -receiverBufferSize)) {
//                    retrieveSymbols(buffer)
                    retrieveSymbolsInC(buffer)

                    carrierPhaseAdjustment = oneComplex

                    selectSymbols.forEachIndexed { i, s ->
                        if (s != null) {
                            processSymbol(s * carrierPhaseAdjustment, i - 1, prevSymbolBoundary)
                        }
                    }
                }

                if (isListeningForCalibration && (recvCalib.offset > -receiverBufferSize)) { /** not else clause, condition updated in [processSymbol] */
                    d("PSK", "extracting calib symbols ${buffer.size}/$receiverBufferSize")
                    recvCalib.extractCalibrationSymbols(buffer, t)
                }

                producerConsumer.putUnusedResource(buffer)

                carrierPhase = (carrierPhase * carrierPhaseAdjustment).normalised()

                carrierPhase *= BUFFER_LENGTH_ROTATION

                t += receiverBufferSize
            }
        }
    }

    private external fun retrieveSymbolsInC(buffer: ShortArray)

    private fun processSymbol(sample: Complex,
            // only needed for test output
                              i: Int,
                              phase: Int
    ) {
        if (aout != null) {
            while (aoutWritten <= t + i * symbolLength + phase) {
                ++aoutWritten
                aout.write((carrierPhase * carrierPhaseAdjustment).normalised().getBytes(1.0))
                sumcorrout?.write(sample.normalised().getBytes(1.0))
            }
        }

        if (!isReceivingSignal) {
            extractPreamble(sample, i, phase)
        } else if (receivedEndOfPreamble < PREAMBLE_TRAILER_STRING.size) {
            extractPreambleTrailer(sample, i, phase)
        } else {
            extractData(sample, i, phase)
        }
    }

    private fun finishReceivingSignal(successfulMessageReceive: Boolean) {
        isReceivingSignal = false
        signalStrength = 50.0
        signalEndedCounter = 0
        symbolsReceived.clear()
        bitsReceived.clear()

        if (successfulMessageReceive) {
            d("PSK", "first four bytes: ${message[0]}, ${message[1]}, ${message[2]}, ${message[3]}, size=${message.size}")
            if (message[0] == message[1] && message[0] == message[2] && message[0] == message[3]) {
                if (message.size >= 52 + (message[0].i and 0xff)) {
                    val msg = ChatMessage(
                            UUID(longOfBytes(message, 4), longOfBytes(message, 12)).toString(),
                            UUID(longOfBytes(message, 20), longOfBytes(message, 28)).toString(),
                            UUID(longOfBytes(message, 36), longOfBytes(message, 44)).toString(),
                            message.subList(52, 52 + (message[0].i and 0xff)).toByteArray().toString(UTF_8)
                    )
                    d("PSK", "Found message $msg")
                    chatMessageReceiveCallback?.invoke(msg)
                }
            }
            else {
                // legacy message without metadata that are used in all the eval Hello, world!s
                val msgString = message.toByteArray().toString(UTF_8)
                messageReceiveCallback(msgString)
            }
        }
        message.clear()

        lock.finishReceiving()
    }

    @Suppress("unused") // called from native code
    private fun startReceivingSignal(signalStrength: Double, axis: Complex, offset: Int) {
        lock.startReceiving()

        d("PSK", "found preamble; signal strength = $signalStrength")
        isReceivingSignal = true
        this.signalStrength = signalStrength
        receivedEndOfPreamble = 0
        this.carrierPhase = axis
        carrierPhaseAdjustment = zeroComplex
        prevSymbolBoundary = offset % receiverBufferSize
        d("PSK", "Start receiving signal")
        lout?.write(("${(t + offset - (PREAMBLE_PHASE_SYNC.size + PREAMBLE_AXIS_SYNC.size) * symbolLength).toFloat() / SAMPLERATE}\t" +
                "${(t + offset - PREAMBLE_PHASE_SYNC.size * symbolLength).toFloat() / SAMPLERATE}\t" +
                "signal start\n").toByteArray())
        lout?.write(("${(t + offset - PREAMBLE_PHASE_SYNC.size * symbolLength).toFloat() / SAMPLERATE}\t" +
                "${(t + offset).toFloat() / SAMPLERATE}\t" +
                "PREAMPLE frequency=$carrierFrequency\n").toByteArray())
    }

    @Deprecated("native code now extracts the preamble")
    private fun extractPreamble(sample: Complex, i: Int, phase: Int) {

        val newBits = PREAMBLE_CONSTELLATION.indexOfMaxBy { it dot sample }
        bitsReceivedPreamble += newBits.b.getBits(3)



        //carrierPhaseAdjustment = (carrierPhaseAdjustment + PREAMBLE_CONSTELLATION[newBits] * !(sample.normalised()) * 0.5).normalised()

        if (bitsReceivedPreamble.size >= PREAMBLE_RECEIVER_BITSTRING.size) {
            val sublist = bitsReceivedPreamble.findSublist(PREAMBLE_RECEIVER_BITSTRING)
            if (sublist != -1) {
                signalStrength =
                        selectSymbols.filterNotNull()
                                .map { it.sqrMagnitude() }
                                .average() / 4
                d("PSK", "found preamble; signal strength = $signalStrength")
                val threshold = 1.0
                if (signalStrength >= threshold) {
                    isReceivingSignal = true
                    receivedEndOfPreamble = 0
                    bitsReceivedPreamble.subList(0, sublist + PREAMBLE_RECEIVER_BITSTRING.size).clear()
                    d("PSK", "Start receiving signal")
                } else {
                    signalStrength = 500.0
                }
            }

            if (!isReceivingSignal && bitsReceivedPreamble.size > PREAMBLE_RECEIVER_BITSTRING.size) {
                bitsReceivedPreamble.subList(0, bitsReceivedPreamble.size - PREAMBLE_RECEIVER_BITSTRING.size).clear()
            }

            lout?.write((
                    "${(t + (i - 1) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                            "${(t + (i) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                            "${if (newBits > 0) if (newBits and 2 > 0) "1" else "0" else "_"}" +
//                            "(${sample.x},${sample.y})" +
                            "\n"
                    ).toByteArray())
        }
    }

    private fun extractPreambleTrailer(sample: Complex, i: Int, phase: Int) {
        if (lout != null) {
            val newBits = CONSTELLATION.indexOf(CONSTELLATION.maxBy { it dot sample })
            d("TRAILER", "newBits: $newBits sample: $sample, expect ${PREAMBLE_TRAILER_STRING[receivedEndOfPreamble]}")
            lout.write((
                    "${(t + (i - 1) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                            "${(t + (i) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                            (if (newBits and 1 > 0) "¹" else "⁰") +
                            (if (newBits and 2 > 0) "¹" else "⁰") +
                            (if (newBits and 4 > 0) "¹" else "⁰") +
                            "\n"
                    ).toByteArray())
        }

        val corr = CONSTELLATION[PREAMBLE_TRAILER_STRING[receivedEndOfPreamble++]] * !sample.normalised()
        if (corr.x < abs(corr.y)) {
            // This is probably not te trailer, we just found the preamble in some noise
            ++signalEndedCounter
            if (signalEndedCounter == 3) {
                finishReceivingSignal(false)
            }
        }

        //carrierPhaseAdjustment = (carrierPhaseAdjustment * 2.0 + carrierPhaseAdjustment * corr).normalised()

        if (isListeningForCalibration && receivedEndOfPreamble == PREAMBLE_TRAILER_STRING.size) {
            recvCalib.offset = (i * symbolLength + phase)
            recvCalib.nextFrequencyIndex = recvCalib.calibStartIndex
        }
    }

    private fun extractData(sample: Complex, i: Int, phase: Int) {
        if (sample.sqrMagnitude() < signalStrength) {
            ++signalEndedCounter
            if (signalEndedCounter == 6) {
                d("PSK", "B")
                finishReceivingSignal(true)
                return
            }
        } else {
            signalEndedCounter = 0
        }

        symbolsReceived += sample

        if (symbolsReceived.size >= BLOCK_SIZE_WITH_ECC) {
            symbolsCallback?.invoke(symbolsReceived)
            val (data, newAxisCorrection) = symbolsReceived.decodeEcc()

            carrierPhaseAdjustment *= newAxisCorrection
            bitsReceived += data

            if (lout != null) {
                data.addECC().toSymbolList().forEachIndexed { j, newBits ->
                    lout.write(
                            ("${(t + (i - BLOCK_SIZE_WITH_ECC + j) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                                    "${(t + (i + 1 - BLOCK_SIZE_WITH_ECC + j) * symbolLength + phase).toFloat() / SAMPLERATE}\t" +
                                    (if (j * 3 >= ECC_DATA_BITS) "C" else "") +
                                    "${newBits and 1}" +
                                    (if (j * 3 + 1 == ECC_DATA_BITS) "C" else "") +
                                    "${(newBits and 2) ushr 1}" +
                                    (if (j * 3 + 2 == ECC_DATA_BITS) "C" else "") +
                                    "${(newBits and 4) ushr 2}\n"
                                    ).toByteArray())
                }
            }

            while (bitsReceived.size >= 8) {
                d("Bits received", bitsReceived.joinToString())
                val newByte = bitsReceived.fromBits()

                lout?.write((
                        "${(t * BITS_PER_SYMBOL + (i + 1) * BITS_PER_SYMBOL * symbolLength - (bitsReceived.size + BITS_PER_SYMBOL + ((bitsReceived.size - 1) / ECC_DATA_BITS + 1) * ECC_CHECKSUM_SIZE) * symbolLength + phase * BITS_PER_SYMBOL).toFloat() / SAMPLERATE / BITS_PER_SYMBOL}\t" +
                                "${(t * BITS_PER_SYMBOL + (i + 1) * BITS_PER_SYMBOL * symbolLength - (bitsReceived.size - 8 + BITS_PER_SYMBOL + ((bitsReceived.size - 9) / ECC_DATA_BITS + 1) * ECC_CHECKSUM_SIZE) * symbolLength + phase * BITS_PER_SYMBOL).toFloat() / SAMPLERATE / BITS_PER_SYMBOL}\t" +
                                "${newByte.toChar()}\n"
                        ).toByteArray())

                message += newByte

                bitsReceived.subList(0, 8).clear()

                testForBeacon(i, phase)

                // TODO send message length
                if (message.size >= 32) {
                    if (message[0] == message[1] && message[0] == message[2] && message[0] == message[3] && (message[0].i and 0xff) > message.size - 52) {
                        // this is a message
                    }
                    else {
                        finishReceivingSignal(true)
                    }
                }
            }


        }
    }

    private fun testForBeacon(i: Int, phase: Int) {
        if (message.size >= 4) {
            if (message[0] == 0.b && message[1] == 0.b && message[2] == 0.b) {
                if (message.size >= 8 && message[3] == ID_BEACON) {
                    d("PSK", "found beacon")
                    // TODO extract device id from message[4:8]

                    isListeningForCalibration = true
                    recvCalib.offset = (i * symbolLength + phase)
                    recvCalib.nextFrequencyIndex = recvCalib.calibStartIndex
                }
                if (message.size > 13 && message[3] == ID_BEACON_RESPONSE) {
                    val l = message[13]
                    if (l >= 0 && message.size > 13 + 2 * l) {
                        // TODO extract device ID

                        val freqs = IntArray(l.i)
                        for (i in 0 until l) {
                            freqs[i] = intOfBytes(message[13 + 2 * i], message[14 + 2 * i], 0, 0)
                        }
                        finishReceivingSignal(false)
                        beaconResponseCallback?.invoke(freqs)
                    }
                }
            }
        }
    }

    companion object {
        init {
            loadLibrary("fftw3")
            loadLibrary("signal-processing")
        }
    }

    private external fun nativeInit(
            SAMPLERATE: Int,
            carrierFrequency: Double,
            SAMPLES_PER_SYMBOL: Int,
            SUMMING_SAMPLE: Int,
            PREAMBLE_SIZE: Int,
            PREAMBLE_TRAILER_STRING_SIZE: Int,
            PREAMBLE_CONSTELLATION_POINTS: DoubleArray,
            PREAMBLE_TRAILER_STRING_CONSTELLATION_POINTS: DoubleArray
    ): Long
    @Suppress("unused")
    private val nativePointer = nativeInit(
            SAMPLERATE,
            carrierFrequency,
            symbolLength,
            symbolWindowSize,
            PREAMBLE_PHASE_SYNC.size,
            PREAMBLE_TRAILER_STRING.size,
            PREAMBLE_PHASE_SYNC.map { CONSTELLATION[it] }.flatMap { arrayListOf(it.x, it.y) }.toDoubleArray(),
            PREAMBLE_TRAILER_STRING.map { CONSTELLATION[it] }.flatMap { arrayListOf(it.x, it.y) }.toDoubleArray()
    )

    // used only for unit testing to close output files used in native code
    private external fun nativeClose()

    private external fun nativeStartRecording()
    private external fun nativePauseRecording()

    private external fun nativeSetCarrierFrequency(value: Double)
    private external fun nativeChangeSymbolLength(newSymbolLength: Int, newWindowSize: Int)

    fun close() {
        nativeClose()
    }

    @Suppress("ProtectedInFinal", "unused")
    protected external fun finalize()
}

package org.batnet.eval

import android.util.Log.d
import android.util.Log.i
import org.batnet.*
import org.junit.Test
import org.batnet.ecc.addECC
import org.batnet.ecc.disableEccCarrierPhaseDriftCorrection
import org.batnet.receiver.PhaseShiftKeyingSignalProcessor
import org.batnet.services.ChannelLock
import org.batnet.utils.*
import org.batnet.utils.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Integer.max
import java.lang.Thread.sleep
import java.util.*

class `Physical layer ` : TestUsingNativeLibraries() {

    /**
     * Phone names:
     *  "lg":   LG K8
     *  "ac2":  Samsung Ace 2
     *  "ac3":  Samsung Ace 3
     *  "n3":   Samsung Nexus 3
     *  "n4":   LG nexus 4
     *  "n5":   LG nexus 5
     */

    val transmitter = "nokia"
    val receiver = "n4"
    val initialFrequency = 19500.0

    @Test
    fun `Calibration`() {

        val ins = FileInputStream("sampledata/c-eval-calib-$transmitter-$receiver.pcm")
        val lout = FileOutputStream("sampledata/label-eval-calib-$transmitter-$receiver.txt")

        val producerConsumer = BufferProducerConsumer<ShortArray, ReceiverEvent> { ShortArray(MAX_RECEIVER_BUFFER_SIZE) }

        val results = HashMap<Int, ArrayList<Double>>()
        var numCallbacks = 0

        val psk = PhaseShiftKeyingSignalProcessor(
                producerConsumer,
                ChannelLock(),
                { i("MESSAGE", it) },
                lout = lout,
                calibrationCallback = {
                    ++numCallbacks
                    val s = it.values.sum()
                    for ((k, v) in it) {
                        results[k]!!.add(v / s)
                    }
                }
        )
        producerConsumer.addEvent(FrequencyChange(initialFrequency))
        psk.isListeningForCalibration = true
        val calibStartIndex = psk.recvCalib.calibStartIndex
        val calibEndIndex = psk.recvCalib.calibEndIndex
        val calibBaseFrequency = psk.recvCalib.calibBaseFrequency
        for (i in calibStartIndex until calibEndIndex) {
            results[i] = ArrayList()
        }

        readInputSoundFile(ins, producerConsumer, psk)
        ins.close()
        psk.close()

        val average = results.mapValues { (_, v) -> v.average() }
        val stddev = results.mapValues { (_, v) -> v.stddev() }
        val sorted = average.values.sortedDescending()
        val rank = average.mapValues { (_, v) -> sorted.indexOf(v) + 1 }

        val output = FileOutputStream("eval/eval-calib-$transmitter-$receiver.csv").bufferedWriter()
        for (i in calibStartIndex until calibEndIndex) {
            output.write(",${i * calibBaseFrequency},")
        }
        output.newLine()
        for (k in 0 until numCallbacks) {
            output.write("$k")
            for (i in calibStartIndex until calibEndIndex) {
                output.write(",${results[i]!![k]},${results.count { it.value[k] >= results[i]!![k] }}")
            }
            output.newLine()
        }
        output.write("average")
        for (i in calibStartIndex until calibEndIndex) {
            output.write(",${average[i]},")
        }
        output.newLine()
        output.write("stddev")
        for (i in calibStartIndex until calibEndIndex) {
            output.write(",${stddev[i]},")
        }
        output.newLine()
        output.write("rank")
        for (i in calibStartIndex until calibEndIndex) {
            output.write(",,${rank[i]}")
        }
        output.newLine()
        output.close()
    }

    @Test
    fun `Changing Frequency`() {
        evaluateTransmissionQuality("changefreq",
                {(it.carrierFrequency / it.recvCalib.calibBaseFrequency + 0.1).i},
                { index, psk -> "${index * psk.recvCalib.calibBaseFrequency}"}
        )
    }

    @Test
    fun `Changing distance`() {
        disableEccCarrierPhaseDriftCorrection()
        evaluateTransmissionQuality("changedist3",
                {it.distanceCm},
                { it, _ -> "$it cm"}
        )
    }

    @Test
    fun `Changing receiver angle z`() {
        evaluateTransmissionQuality("changerecvanglez",
                {it.distanceCm},
                { it, _ -> "$it°"}
        )
    }

    @Test
    fun `Changing receiver angle y`() {
        evaluateTransmissionQuality("changerecvangley",
                {it.distanceCm},
                { it, _ -> "$it°"}
        )
    }

    @Test
    fun `Changing transmitter angle z`() {
        evaluateTransmissionQuality("changetransmanglez",
                {it.distanceCm},
                { it, _ -> "$it°"}
        )
    }

    @Test
    fun `Changing transmitter angle Y`() {
        evaluateTransmissionQuality("changetransmangley",
                {it.distanceCm},
                { it, _ -> "$it°"}
        )
    }

    @Test
    fun `Changing symbol length`() {
        evaluateTransmissionQuality("changelen",
                {(it.symbolWindowSize shl 16) or it.symbolLength},
                { it, _ -> "${it ushr 16}/${it and 0xffff}" }
        )
    }

    @Test
    fun `Change nothing`() {
        evaluateTransmissionQuality("changenoth",
                {it.distanceCm},
                { it, _ -> "$it"}
        )
    }

    @Test
    fun `Change things randomly`() {
        disableEccCarrierPhaseDriftCorrection()
        evaluateTransmissionQuality("changerand2",
                {it.distanceCm},
                { it, _ -> "$it"}
        )
    }

    private var receiverBufferSize = RECEIVER_BUFFER_SIZE

    private fun readInputSoundFile(ins: FileInputStream, producerConsumer: BufferProducerConsumer<ShortArray, in ReceiverEvent>, signalProcessor: PhaseShiftKeyingSignalProcessor) {
        val insBytes = ByteArray(MAX_RECEIVER_BUFFER_SIZE * 2)
        while (ins.read(insBytes, 0, MIN_RECEIVER_BUFFER_SIZE * 2) != -1) {
            while (insBytes[0] == 0x00.b && insBytes[1] == 0x80.b) {
                val (event, numberOfRemovedBytes) = insBytes.readEvent()
                if (event != null) {
                    producerConsumer.addEvent(event)
                    d("Event", "$event")
                    when (event) {
                        is SymbolLengthChange -> {
                            receiverBufferSize = event.samplesPerSymbol * PREAMBLE_PHASE_SYNC.size
                        }
                    }
                    for (j in numberOfRemovedBytes until MIN_RECEIVER_BUFFER_SIZE * 2) {
                        insBytes[j - numberOfRemovedBytes] = insBytes[j]
                    }
                    ins.read(insBytes, MIN_RECEIVER_BUFFER_SIZE * 2 - numberOfRemovedBytes, numberOfRemovedBytes)
                }
            }
            ins.read(insBytes, MIN_RECEIVER_BUFFER_SIZE * 2, (receiverBufferSize - MIN_RECEIVER_BUFFER_SIZE) * 2)
            producerConsumer.produce { buffer ->
                insBytes.asList().subList(0, receiverBufferSize * 2).chunked(2).forEachIndexed { index, list ->
                    buffer[index] = intOfBytes(list[0], list[1], 0, 0).s
                }
            }
        }

        while (producerConsumer.hasNext()) {
            sleep(100)
        }

        sleep(100)  // make sure last iteration of symbol extraction is finished so files can be closed
    }

    private fun evaluateTransmissionQuality(
            testname: String,
            bucketId: (PhaseShiftKeyingSignalProcessor) -> Int,
            bucketNames: (Int, PhaseShiftKeyingSignalProcessor) -> String
    ) {

        val ins = FileInputStream("sampledata/c-eval-$testname-$transmitter-$receiver.pcm")
        val lout = FileOutputStream("sampledata/label-eval-$testname-$transmitter-$receiver.txt")

        val producerConsumer = BufferProducerConsumer<ShortArray, ReceiverEvent> { ShortArray(MAX_RECEIVER_BUFFER_SIZE) }

        var current = ArrayList<Complex>()
        val results = TreeMap<Int, ArrayList<List<Complex>>>()

        var psk : PhaseShiftKeyingSignalProcessor? = null
        var numCallbacks = 0
        psk = PhaseShiftKeyingSignalProcessor(
                producerConsumer,
                ChannelLock(),
                {
                    val f = bucketId(psk!!)
                    val s = results.getOrPut(f) { arrayListOf() }
                    s += current
                    numCallbacks = max(numCallbacks, s.size)
                    current = ArrayList()
                    i("MESSAGE", it)
                },
                lout = lout,
                symbolsCallback = {
                    current.addAll(it)
                }
        )
        producerConsumer.addEvent(FrequencyChange(initialFrequency))

        readInputSoundFile(ins, producerConsumer, psk)
        ins.close()
        psk.close()
        lout.close()

        val referencePoints = "Hello, world!".toBitList().addECC().toSymbolList()
        /*val referenceSymbols = referencePoints.map { CONSTELLATION[it] }
        val score = results.mapValues { (k, v) -> v.map { samples ->
                    (samples zip referenceSymbols)
                            .sumByDouble { (s, r) ->
                                s dot r
                            } / samples.sumByDouble { it.magnitude() } / referenceSymbols.size
        } }*/
        val score = results.mapValues { (k, v) -> v.map { samples ->
            (samples zip referencePoints).sumByDouble { (s, r) ->
                when (CONSTELLATION.indexOfMaxBy { s dot it }) {
                    r.i -> 1.0
                    (r.i - 1 + CONSTELLATION.size) % CONSTELLATION.size -> 0.5
                    (r.i + 1) % CONSTELLATION.size -> 0.5
                    else -> 0.0
                }
            } / referencePoints.size
        } }
        val average = score.mapValues { (_, v) -> v.average() }
        val stddev = score.mapValues { (_, v) -> v.stddev() }

        val output = FileOutputStream("eval/eval-$testname-$transmitter-$receiver.csv").bufferedWriter()
        val keys = score.keys.toList() // don't want to change order
        for (i in keys) {
            output.write(",${bucketNames(i, psk)}")
        }
        output.newLine()
        for (k in 0 until numCallbacks) {
            output.write("$k")
            for (i in keys) {
                output.write(",${score.getValue(i).getOrNull(k)}")
            }
            output.newLine()
        }
        output.write("average")
        for (i in keys) {
            output.write(",${average[i]}")
        }
        output.newLine()
        output.write("stddev")
        for (i in keys) {
            output.write(",${stddev[i]}")
        }
        output.newLine()
//        output.write("rank")
//        for (i in calibStartIndex until CALIB_END_INDEX) {
//            output.write(",${rank[i]}")
//        }
//        output.newLine()
        output.close()

    }
}
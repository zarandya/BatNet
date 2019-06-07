package org.batnet.calib

import org.batnet.*
import org.batnet.utils.toBitList
import org.batnet.transmitter.PhaseShiftKeyingSignalGenerator
import kotlin.math.PI
import kotlin.math.cos


const val ID_BEACON: Byte = 1
const val ID_BEACON_RESPONSE: Byte = 7

class SenderCalibration(val signalGenerator: PhaseShiftKeyingSignalGenerator) {


    val frequency get() = signalGenerator.frequency
    val senderSamplerate = signalGenerator.senderSamplerate
    var symbolLength = SAMPLES_PER_SYMBOL
        private set
    var symbolWindowSize = SYMBOL_WINDOW_SIZE
        private set

    private var samplesPerFrequency = symbolWindowSize * 5
    var calibBaseFrequency = SAMPLERATE.toDouble() / symbolWindowSize
        private set
    var calibStartIndex = (18000/calibBaseFrequency).toInt()
        private set
    var calibEndIndex = symbolWindowSize / 2
        private set


    private val OMEGA get() = 2.0 * PI * frequency / SAMPLERATE

    private val transitionLength = symbolLength - symbolWindowSize
    private val transitionFrequencyOffsetBase = SAMPLERATE.d / transitionLength / CONSTELLATION.size

    fun generateSenderCalibrationSignal(minBufferSize: Int = 0): ShortArray {
        var a = 0.0
        val frequencies = (calibStartIndex until calibEndIndex)
                .map { calibBaseFrequency * it }
        val symbols = PREAMBLE_SENDER_STRING
        var prevSymbol = symbols[0]
        val output = ShortArray((symbols.size * symbolLength + frequencies.size * samplesPerFrequency) * senderSamplerate / SAMPLERATE)

        symbols.forEachIndexed { i, s ->
            val transitionFreqeuncyOffset = ((CONSTELLATION_EXPONENTS[prevSymbol] - CONSTELLATION_EXPONENTS[s] + CONSTELLATION.size) % CONSTELLATION.size) * transitionFrequencyOffsetBase
            val transitionFrequency = frequency + transitionFreqeuncyOffset
            val transitionOmega = 2.0 * PI * transitionFrequency / SAMPLERATE
            val transitionInitialPhase = (OMEGA - transitionOmega) * i * symbolLength - CONSTELLATION_EXPONENTS[prevSymbol] * 2.0 * PI / CONSTELLATION.size
            val senderTransitionOmega = transitionOmega * SAMPLERATE / senderSamplerate
            for (k in i * symbolLength * senderSamplerate / SAMPLERATE until (i * symbolLength + transitionLength) * senderSamplerate / SAMPLERATE) {
                output[k] = (cos(senderTransitionOmega * k + transitionInitialPhase) * Short.MAX_VALUE).s
            }

            prevSymbol = s

            val initialPhase = -CONSTELLATION_EXPONENTS[s] * 2.0 * PI / CONSTELLATION.size
            val senderOmega = OMEGA * SAMPLERATE / senderSamplerate
            for (k in (i * symbolLength + transitionLength) * senderSamplerate / SAMPLERATE until (i+1) * symbolLength * senderSamplerate / SAMPLERATE) {
                output[k] = (cos(senderOmega * k + initialPhase) * Short.MAX_VALUE).s
            }
        }

        val frequenciesStart = symbols.size * symbolLength * senderSamplerate / SAMPLERATE

        frequencies.forEachIndexed { index, f ->
            for (j in 0 until samplesPerFrequency * senderSamplerate / SAMPLERATE) {
                a += f
                if (a > senderSamplerate) {
                    a -= senderSamplerate
                }
                output[frequenciesStart + index * samplesPerFrequency * senderSamplerate / SAMPLERATE + j] =
                        (cos(2.0 * PI * a / senderSamplerate) * Short.MAX_VALUE / 2).s
            }
        }
        return output
    }

    fun generateSenderBeacon(): ShortArray {
        val beaconData = ByteArray(8)
        beaconData[0] = 0
        beaconData[1] = 0
        beaconData[2] = 0
        beaconData[3] = ID_BEACON
        // TODO add device ID

        val frequencies = (calibStartIndex until calibEndIndex)
                .map { calibBaseFrequency * it }

        val padLength = frequencies.size * samplesPerFrequency * senderSamplerate / SAMPLERATE
        val output = signalGenerator.generateSignal(beaconData.toBitList(), padArrayWithSamples = padLength)

        val frequenciesStart = output.size - padLength

        var a = 0.0
        frequencies.forEachIndexed { index, f ->
            for (j in 0 until samplesPerFrequency * senderSamplerate / SAMPLERATE) {
                a += f
                if (a > senderSamplerate) {
                    a -= senderSamplerate
                }
                output[frequenciesStart + index * samplesPerFrequency * senderSamplerate / SAMPLERATE + j] =
                        (cos(2.0 * PI * a / senderSamplerate) * Short.MAX_VALUE / 2).s
            }
        }
        return output

    }

    fun generateBeaconResponse(suitableFrequencies: Set<Int>): ShortArray {
        val beaconData = ByteArray(13 + 2 * suitableFrequencies.size)
        beaconData[0] = 0
        beaconData[1] = 0
        beaconData[2] = 0
        beaconData[3] = ID_BEACON_RESPONSE
        // TODO add device IDs at beaconData[4:8] and [8:12]

        beaconData[12] = suitableFrequencies.size.b
        suitableFrequencies.forEachIndexed { index, i ->
            beaconData[13 + index * 2] = (i and 0xff).b
            beaconData[14 + index * 2] = (i ushr 8).b
        }

        return signalGenerator.generateSignal(beaconData.toBitList())
    }

    fun changeSymbolLength(newSymbolLength: Int, newSymbolWindowSize: Int) {
        symbolLength = newSymbolLength
        symbolWindowSize = newSymbolWindowSize
        samplesPerFrequency = symbolWindowSize * 5
        calibBaseFrequency = SAMPLERATE.toDouble() / symbolWindowSize
        calibStartIndex = (18000/calibBaseFrequency).toInt()
        calibEndIndex = symbolWindowSize / 2
    }
}
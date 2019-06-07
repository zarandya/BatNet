package org.batnet.transmitter

import android.util.Log.d
import org.batnet.*
import org.batnet.ecc.addECC
import org.batnet.utils.BitList
import org.batnet.utils.SymbolList
import org.batnet.utils.toBitList
import org.batnet.utils.dot
import org.batnet.utils.polar
import java.lang.Math.*

const val MIN_TRANSMISSION_LENGTH = 0

class PhaseShiftKeyingSignalGenerator(val senderSamplerate: Int = SAMPLERATE) {

    var frequency = MID_FREQUENCY
    var symbolLength = SAMPLES_PER_SYMBOL
        private set
    var symbolWindowSize = SYMBOL_WINDOW_SIZE
        private set

    private val amplitude: Short = 2500

    private val OMEGA get() = 2.0 * PI * frequency / SAMPLERATE

    private val transitionLength get() = symbolLength - symbolWindowSize
    private val transitionFrequencyOffsetBase get() = SAMPLERATE.d / transitionLength / CONSTELLATION.size

    @Deprecated("Very slow, also doesn't have frequency shift at discontinuity")
    fun generateSignal(message: String) = (PREAMBLE_SENDER_STRING +
            message.toBitList().addECC().toSymbolList()
            )
            .flatMap { d -> (0 until symbolLength).map { d } }
            .mapIndexed { t, d -> polar(1.0, OMEGA * t) dot CONSTELLATION[d] }
            .map { (it * amplitude).s }

    fun generateSignal2(message: String): ShortArray {
        val symbols = SymbolList(PREAMBLE_SENDER_STRING + message.toBitList().addECC().toSymbolList())
        return generateSignalFromSymbols(symbols)
    }

    fun generateSignal(message: BitList, padArrayWithSamples: Int = 0): ShortArray {
        return generateSignalFromSymbols(SymbolList(PREAMBLE_SENDER_STRING + message.addECC().toSymbolList()), padArrayWithSamples = padArrayWithSamples)
    }

    fun generateSignalFromSymbols(symbols: SymbolList, padArrayWithSamples: Int = 0): ShortArray {
        var prevSymbol = symbols[0]
        val output = ShortArray(max((symbols.size.l * symbolLength * senderSamplerate / SAMPLERATE + padArrayWithSamples).i, MIN_TRANSMISSION_LENGTH))
        d("Generate signal", "${symbols.size}*$symbolLength*$senderSamplerate/$SAMPLERATE=${symbols.size * symbolLength * senderSamplerate / SAMPLERATE}")

        symbols.forEachIndexed { i, s ->
            val transitionFreqeuncyOffset = ((CONSTELLATION_EXPONENTS[prevSymbol] - CONSTELLATION_EXPONENTS[s] + CONSTELLATION.size) % CONSTELLATION.size) * transitionFrequencyOffsetBase
            val transitionFrequency = frequency + transitionFreqeuncyOffset
            val transitionOmega = 2.0 * PI * transitionFrequency / SAMPLERATE
            val transitionInitialPhase = (OMEGA - transitionOmega) * i * symbolLength - CONSTELLATION_EXPONENTS[prevSymbol] * 2.0 * PI / CONSTELLATION.size
            val senderTransitionOmega = transitionOmega * SAMPLERATE / senderSamplerate
            for (k in i.l * symbolLength * senderSamplerate / SAMPLERATE until (i * symbolLength + transitionLength).l * senderSamplerate / SAMPLERATE) {
                output[k] = (cos(senderTransitionOmega * k + transitionInitialPhase) * Short.MAX_VALUE).s
            }

            prevSymbol = s

            val initialPhase = -CONSTELLATION_EXPONENTS[s] * 2.0 * PI / CONSTELLATION.size
            val senderOmega = OMEGA * SAMPLERATE / senderSamplerate
            for (k in (i * symbolLength + transitionLength).l * senderSamplerate / SAMPLERATE until (i+1).l * symbolLength * senderSamplerate / SAMPLERATE) {
                output[k] = (cos(senderOmega * k + initialPhase) * Short.MAX_VALUE).s
            }
        }

        return output
    }

    fun generateJammingSignal(): ShortArray {
        val n = symbolWindowSize * 100
        val signal = ShortArray(n)
        val omega = OMEGA * SAMPLERATE / senderSamplerate
        for (i in 0 until n) {
            signal[i] = (5000 * cos(omega * i)).s
        }
        return signal
    }

    // TODO make this thread safe
    fun changeSymbolLength(newSymbolLength: Int, newSymbolWindowSize: Int) {
        symbolLength = newSymbolLength
        symbolWindowSize = newSymbolWindowSize
    }
}
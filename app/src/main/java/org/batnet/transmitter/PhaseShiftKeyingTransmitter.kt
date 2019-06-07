package org.batnet.transmitter

import android.media.*
import android.media.AudioFormat.CHANNEL_OUT_MONO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioTrack.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.LOLLIPOP
import android.os.Handler
import android.util.Log.d
import org.batnet.SAMPLERATE
import org.batnet.b
import org.batnet.calib.SenderCalibration
import org.batnet.getBytes
import org.batnet.s
import org.batnet.services.ITransmitterChannelLock
import org.batnet.utils.BitList
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.max
import kotlin.text.Charsets.UTF_8

class PhaseShiftKeyingTransmitter(val lock: ITransmitterChannelLock) {

    private val nativeSamplerate = getNativeOutputSampleRate(STREAM_MUSIC)
    private val bufferSize = getMinBufferSize(SAMPLERATE, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT)

    init {
        d("NATIVE_SAMPLERATE", "AudioTrack: $nativeSamplerate")
        d("OUTPUT_BUFFER_SIZE", "$bufferSize")
    }

    private val audioTrack =
            if (SDK_INT >= LOLLIPOP) {
                AudioTrack(
                        AudioAttributes.Builder().build(),
                        AudioFormat.Builder().setChannelMask(CHANNEL_OUT_MONO).setSampleRate(nativeSamplerate).setEncoding(ENCODING_PCM_16BIT).build(),
                        bufferSize, MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(STREAM_MUSIC, nativeSamplerate, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT, bufferSize, MODE_STREAM)
            }

    private val signalGenerator = PhaseShiftKeyingSignalGenerator(nativeSamplerate)
    val calibration = SenderCalibration(signalGenerator)

    var frequency
        get() = signalGenerator.frequency
        set(value) {
            signalGenerator.frequency = value
        }

    fun setFrequencyIndex(index: Int) {
        frequency = index * calibration.calibBaseFrequency
    }

    fun changeSymbolLength(symbolLength: Int, windowSize: Int) {
        signalGenerator.changeSymbolLength(symbolLength, windowSize)
        calibration.changeSymbolLength(symbolLength, windowSize)
    }

    init {
        // play startup sound
        val signal = ShortArray(max(9600, bufferSize))
        for (i in 0 until 9600) {
            signal[i] = (1000 * cos(440.0 * i / nativeSamplerate)).s
        }
        audioTrack.write(signal, 0, signal.size)
        audioTrack.play()
    }

    val handler = Handler()

    fun playSound(message: String, onFinished: (() -> Unit)? = null) {
        val signal = signalGenerator.generateSignal2(message)
        playSignal(signal, onFinished)
    }


    fun playCalibration() {
        val signal = calibration.generateSenderCalibrationSignal(bufferSize)
        playSignal(signal, null)
    }

    fun sendBeaconResponse(suitableFrequencies: Set<Int>, onFinished: (() -> Unit)? = null) {
        val signal = calibration.generateBeaconResponse(suitableFrequencies)
        playSignal(signal, onFinished)
    }

    fun sendBeacon(onFinished: (() -> Unit)? = null) {
        val signal = calibration.generateSenderBeacon()
        playSignal(signal, onFinished)
    }

    fun playMessage(id: String, author: String, content: String, dialog: String, onFinished: (() -> Unit)? = null) {
        val uuid = UUID.fromString(id)
        val authorUuid = UUID.fromString(author)
        val dialogUuid = UUID.fromString(dialog)

        val bytes = content.toByteArray(UTF_8)

        val messageBits = BitList()
        messageBits += byteArrayOf(bytes.size.b, bytes.size.b, bytes.size.b, bytes.size.b)
        messageBits += uuid.leastSignificantBits.getBytes()
        messageBits += uuid.mostSignificantBits.getBytes()
        messageBits += authorUuid.leastSignificantBits.getBytes()
        messageBits += authorUuid.mostSignificantBits.getBytes()
        messageBits += dialogUuid.leastSignificantBits.getBytes()
        messageBits += dialogUuid.mostSignificantBits.getBytes()
        messageBits += content.toByteArray()

        val signal = signalGenerator.generateSignal(messageBits)

        playSignal(signal, onFinished)
    }

    private fun playSignal(signal: ShortArray, onFinished: (() -> Unit)?) {
        thread(name = "Sound playing thread") {
            lock.startTransmitting()

            audioTrack.pause()
            audioTrack.flush()

            d("Transmitter", "gonna write")
            var i = audioTrack.write(signal, 0, signal.size)

            d("Transmitter", "gonna set marker")
            audioTrack.notificationMarkerPosition = signal.size - 1
            audioTrack.setPlaybackPositionUpdateListener(object : OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    d("Transmittar", "finish transmitting")
                    lock.finishTransmitting()
                    onFinished?.invoke()
                    audioTrack.stop()
                }

                override fun onPeriodicNotification(track: AudioTrack?) {
                }

            }, handler)

            d("Transmitter", "gonna play")
            audioTrack.play()

            while (i < signal.size) {
                i += audioTrack.write(signal, i, signal.size - i)
            }
        }
    }

    private var isJamming = false

    // used by eval, only called from main activity, never service, doesn't use lock
    fun startJammingOneFrequency() {
        d("JAMMING", "start jamming, freq=$frequency")
        thread(name = "Jamming thread") {
            val signal = signalGenerator.generateJammingSignal()
            audioTrack.pause()
            audioTrack.flush()
            var i = audioTrack.write(signal, 0, signal.size / 2)
            audioTrack.positionNotificationPeriod = i / 2
            audioTrack.setPlaybackPositionUpdateListener(object : OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                }

                override fun onPeriodicNotification(track: AudioTrack?) {
                }
            })
            isJamming = true
            audioTrack.play()
            while (isJamming) {
                d("JAMMING", "Write more signal $i/${signal.size}")
                i += audioTrack.write(signal, i, signal.size - i)
                d("JAMMING", "wrote mmore, now at $i")
                i %= signal.size
            }
        }
    }

    fun stopJammingOneFrequency() {
        d("JAMMING", "stop jamming")
        isJamming = false
        audioTrack.pause()
        audioTrack.flush()
    }
}


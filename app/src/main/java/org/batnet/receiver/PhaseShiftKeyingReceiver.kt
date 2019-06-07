package org.batnet.receiver

import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioRecord
import android.media.AudioRecord.getMinBufferSize
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.Log.d
import org.batnet.*
import org.batnet.services.ChannelLock
import org.batnet.utils.*
import org.batnet.utils.*
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class PhaseShiftKeyingReceiver(val lock: ChannelLock, messageReceivedCallback: (UltrasoundMessage) -> Unit) {

    init {
        for (sampleRate in arrayOf(8000, 11025, 16000, 22050,
                32000, 37800, 44056, 44100, 47250, 48000, 50000, 50400, 88200,
                96000, 176400, 192000, 352800, 2822400, 5644800)) {
            val r = getMinBufferSize(sampleRate, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
            if (r > 0) {
                d("NATIVE_SAMPLERATE", "AudioRecord $sampleRate supported wit buffer size=$r")
            }
        }
    }


    private val recorderBufferSize = getMinBufferSize(SAMPLERATE, CHANNEL_IN_MONO, ENCODING_PCM_16BIT) * 64

    private val recorder = AudioRecord(MIC, SAMPLERATE, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, recorderBufferSize)

    init {
        d("ACTUAL_REC_SAMPLERATE", "${recorder.sampleRate}")
    }

    private val producerConsumer = BufferProducerConsumer<ShortArray, ReceiverEvent> { ShortArray(MAX_RECEIVER_BUFFER_SIZE) }
    private val iOProducerConsumer = BufferProducerConsumer<ShortArray, ReceiverEvent> { ShortArray(MAX_RECEIVER_BUFFER_SIZE) }

    private lateinit var os: BufferedOutputStream

    private object loutWrapped : OutputStream() {
        lateinit var lout: BufferedOutputStream
        override fun write(b: Int) {
            lout.write(b)
        }

    }

    @Volatile
    var isRecording = false
        private set

    private val processor = PhaseShiftKeyingSignalProcessor(
            iOProducerConsumer,
            lock,
            { message -> messageReceivedCallback(TextMessage(message)) },
            lout = loutWrapped,
            chatMessageReceiveCallback = messageReceivedCallback,
            calibrationCallback = { freqs -> thread { messageReceivedCallback(BeaconMessage(freqs)) } },
            beaconResponseCallback = { freqs -> thread { messageReceivedCallback(BeaconResponse(freqs)) } }
    )

    private val preambleSize = PREAMBLE_PHASE_SYNC.size
    private var receiverBufferSize = SAMPLES_PER_SYMBOL * preambleSize

    val frequency get() = processor.carrierFrequency
    val isReceivingSignal get() = processor.isReceivingSignal

    // private: can only be entered from signal reading thread through lock
    private fun start() {
        processor.isListeningForCalibration = false
        if (isRecording) return
        os = BufferedOutputStream(FileOutputStream("${System.getenv("EXTERNAL_STORAGE")}/c.pcm"))
        producerConsumer.addEvent(StartRecording)
        os.writeEvent(SymbolLengthChange(processor.symbolLength, processor.symbolWindowSize))
        loutWrapped.lout = BufferedOutputStream(FileOutputStream("${System.getenv("EXTERNAL_STORAGE")}/label.txt"))
        isRecording = true
        recorder.startRecording()

        d("PSK record bufsize", "$recorderBufferSize")
    }

    private fun pause() {
        if (!isRecording) return
        producerConsumer.addEvent(PauseRecording)
        isRecording = false
//        os.flush()
//        loutWrapped.lout.flush()
        recorder.stop()
    }

    fun listenForCalibration() {
        processor.isListeningForCalibration = true
    }

    init {
        thread(name = "Signal reading thread") {
            start()

            while (true) {
                lock.waitUntilCanReadSignal(::pause, ::start)
                producerConsumer.produce { buffer ->
                    recorder.readBlocking(buffer, receiverBufferSize)
                }
            }

        }
    }

    init {
        thread(name = "Signal output thread") {
            while (!isRecording) {
                sleep(100)
            }
            while (true) {
                val buffer = producerConsumer.consume {
//                    os.writeEvent(it)
                    iOProducerConsumer.addEvent(it)
                    when (it) {
                        is SymbolLengthChange -> receiverBufferSize = it.samplesPerSymbol * preambleSize
//                        is PauseRecording -> os.flush()
                    }
                }
                if (buffer[0] == 0x8000.s) {
                    buffer[0] = 0x8001.s
                }
                iOProducerConsumer.putResource(buffer)
//                synchronized(os) {
//                    for (i in 0 until receiverBufferSize) {
//                        os.write(buffer[i].i and 0xff)
//                        os.write(buffer[i].i ushr 8)
//                    }
//                }
                producerConsumer.putUnusedResource(iOProducerConsumer.getResource())
            }
        }
    }

    fun processEvent(e: ReceiverEvent) {
        synchronized(this) {
            when (e) {
                StartRecording -> lock.manuallyStartRecording()
                PauseRecording -> lock.manuallyPauseRecording()
                else -> producerConsumer.addEvent(e)
            }
        }
    }

}

fun AudioRecord.readBlocking(buffer: ShortArray, bufferSize: Int) =
        if (SDK_INT >= VERSION_CODES.M) {
            read(buffer, 0, bufferSize, AudioRecord.READ_BLOCKING)
        } else {
            var i = 0
            while (i < bufferSize) {
                i += read(buffer, i, bufferSize - i)
            }
            bufferSize
        }


package org.batnet

import android.Manifest.permission.RECORD_AUDIO
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.*
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION.SDK_INT
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Message.obtain
import android.support.v4.app.ActivityCompat
import android.util.Log.d
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_main.*
import org.batnet.services.*
import org.batnet.R.layout.support_simple_spinner_dropdown_item
import org.batnet.R.string.*
import org.batnet.services.*
import org.batnet.transmitter.PhaseShiftKeyingTransmitter
import org.batnet.ui.DialogsActivity
import org.batnet.utils.*
import org.batnet.R
import org.batnet.utils.*
import java.lang.Integer.parseInt
import kotlin.concurrent.fixedRateTimer

class MainActivity : AppCompatActivity() {

    val REQUEST_RECORD_AUDIO_PERMISSION = 200

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (SDK_INT >= VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, arrayOf(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE), REQUEST_RECORD_AUDIO_PERMISSION)
        }
        else {
            startSignalProcessing()
        }

    }

    private fun startSignalProcessing() {
        val audioManager = baseContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isSpeakerphoneOn = true
        audioManager.isBluetoothScoOn = false
        audioManager.mode = MODE_IN_CALL

        val transmitter = PhaseShiftKeyingTransmitter(object : ITransmitterChannelLock {
            override fun startTransmitting() {}
            override fun finishTransmitting() {}
        })

        IMPULSE_TEST_SEND_BUTTON.setOnClickListener { transmitter.playSound(getString(helloworld)) }

        SEND_16_SIGNALS.setOnClickListener {
            var i = 0
            fixedRateTimer(period = 1000) {
                transmitter.playSound(getString(helloworld))
                ++i
                if (i >= 16) {
                    cancel()
                }
            }
        }

        val receiver = bindSignalProcessingService { true }

        START_RECORDING_BUTTON.setOnClickListener {
            if (START_RECORDING_BUTTON.text == getString(pause_recording)) {
                d("PAUSE", "sending message")
                receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, PauseRecording))
                d("PAUSE", "message sent, changing button caption")
                START_RECORDING_BUTTON.text = getString(start_recording)
            }
            else {
                receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, StartRecording))
                START_RECORDING_BUTTON.text = getString(pause_recording)
            }
        }

        OPEN_CHATKIT_BUTTON.setOnClickListener {
            startActivity(Intent(this, DialogsActivity::class.java))
        }

        PLAY_CALIB_BUTTON.setOnClickListener {
            transmitter.playCalibration()
        }

        RECEIVE_CALIB_BUTTON.setOnClickListener {
            receiver.messenger?.send(obtain(null, MSG_RECEIVER_LISTEN_FOR_CALIB))
        }

        initFrequencySpinner(transmitter, receiver, (MID_FREQUENCY / transmitter.calibration.calibBaseFrequency).i)
        initSymbolLengthSpinner(transmitter, receiver)

        DISTANCE_UPDATE_BUTTON.setOnClickListener {
            val cm = parseInt(DISTANCE_INPUT.text.toString())
            d("Change distance", "$cm cm")
            receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, DistanceChange(cm)))
        }

        TOGGLE_JAMMING_BUTTON.setOnClickListener {
            if (TOGGLE_JAMMING_BUTTON.text == getString(START_JAMMING)) {
                TOGGLE_JAMMING_BUTTON.text = getString(STOP_JAMMING)
                transmitter.startJammingOneFrequency()
            }
            else {
                TOGGLE_JAMMING_BUTTON.text = getString(START_JAMMING)
                transmitter.stopJammingOneFrequency()
            }
        }
    }

    private fun initFrequencySpinner(transmitter: PhaseShiftKeyingTransmitter, receiver: WithMessenger, defaultIndex: Int = transmitter.calibration.calibStartIndex) {
        val frequencies = (transmitter.calibration.calibStartIndex until transmitter.calibration.calibEndIndex).map { it * transmitter.calibration.calibBaseFrequency }
        FREQUENCY_SPINNER.adapter = ArrayAdapter(this, support_simple_spinner_dropdown_item, frequencies)
        FREQUENCY_SPINNER.setSelection(defaultIndex - transmitter.calibration.calibStartIndex)
        FREQUENCY_SPINNER.onItemSelectedListener = object : OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val f = frequencies[id.i]
                d("Change Frequency", "$f")
                transmitter.frequency = f
                receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, FrequencyChange(f)))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initSymbolLengthSpinner(transmitter: PhaseShiftKeyingTransmitter, receiver: WithMessenger) {
        val symbolLengths = listOf(
                SymbolLengthChange(SAMPLES_PER_SYMBOL, SYMBOL_WINDOW_SIZE),
                SymbolLengthChange(36, 30),
                SymbolLengthChange(40, 32),
                SymbolLengthChange(48, 36),
                SymbolLengthChange(54, 40),
                SymbolLengthChange(64, 48),
                SymbolLengthChange(72, 54),
                SymbolLengthChange(80, 64),
                SymbolLengthChange(90, 70),
                SymbolLengthChange(96, 72),
                SymbolLengthChange(128, 100),
                SymbolLengthChange(160, 128),
                SymbolLengthChange(192, 144),
                SymbolLengthChange(224, 160),
                SymbolLengthChange(256, 192),
                SymbolLengthChange(320, 256)
        )
        val labels = symbolLengths.map { "${it.windowSize}/${it.samplesPerSymbol}" }

        SYMBOL_LENGTH_SPINNER.adapter = ArrayAdapter(this, support_simple_spinner_dropdown_item, labels)
        SYMBOL_LENGTH_SPINNER.onItemSelectedListener = object: OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val event = symbolLengths[id.i]
                transmitter.changeSymbolLength(event.samplesPerSymbol, event.windowSize)
                val baseFrequency = transmitter.calibration.calibBaseFrequency
                val newFrequencyIndex = ((transmitter.frequency + baseFrequency / 2) / baseFrequency).i
                transmitter.setFrequencyIndex(newFrequencyIndex)

                receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, event))
                receiver.messenger?.send(obtain(null, MSG_RECEIVER_EVENT, FrequencyChange(baseFrequency * newFrequencyIndex)))

                initFrequencySpinner(transmitter, receiver, newFrequencyIndex)
            }

        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        startSignalProcessing()
    }

}

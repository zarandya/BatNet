package org.batnet.services

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Message.obtain
import android.os.Messenger
import android.util.Log.d
import org.batnet.App.Companion.db
import org.batnet.db.*
import org.batnet.receiver.*
import org.batnet.db.*
import org.batnet.receiver.*
import org.batnet.transmitter.PhaseShiftKeyingTransmitter
import org.batnet.ui.SignalProcessingActiveNotification
import org.batnet.ui.SignalProcessingActiveNotification.notify
import org.batnet.utils.*
import org.batnet.utils.*
import java.lang.System.currentTimeMillis
import java.lang.Thread.sleep
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread
import kotlin.random.Random.Default.nextInt

const val MSG_REGISTER_CLIENT = 1
const val MSG_UNREGISTER_CLIENT = 2
const val MSG_SEND_MESSAGE = 3
const val MSG_RECEIVE_MESSAGE = 4
@Deprecated("use MSG_RECEIVER_EVENT instead") const val MSG_RECEIVER_PAUSE = 5
@Deprecated("use MSG_RECEIVER_EVENT instead") const val MSG_RECEIVER_RESUME = 6
const val MSG_RECEIVER_LISTEN_FOR_CALIB = 7
@Deprecated("use MSG_RECEIVER_EVENT instead") const val MSG_CHANGE_FREQUENCY = 8
const val MSG_RECEIVER_EVENT = 9

class SignalProcessingService : Service() {

    private val channelLock = ChannelLock()
    private val receiver = PhaseShiftKeyingReceiver(channelLock) {
        thread {
            when (it) {
                is TextMessage -> { // legacy text message use default conversation and sender
//                    val message = Message(UUID.randomUUID().toString(), it.msg, them.ID, Date(), dialog.ID)
//                    db.messages += message
//
//                    for (c in clients) {
//                        c.send(obtain(null, MSG_RECEIVE_MESSAGE, message.ID))
//                    }
                }
                is ChatMessage -> {
                    val dialog = db.dialogs[it.dialog]
                    if (dialog != null) {
                        // This message is for me
                        d("Service", "received message for dialog $dialog")
                        if (db.users[it.sender] == null) {
                            d("Service", "unknown sender ${it.sender}")
                            db.users += User(it.sender, "unknown user")
                        }
                        val message = Message(UUID.randomUUID().toString(), it.text, it.sender, Date(), it.dialog)
                        db.messages += message

                        for (c in clients) {
                            c.send(obtain(null, MSG_RECEIVE_MESSAGE, message.ID))
                        }
                    }
                    if (dialog == null || db.dialogs.getUsers(dialog.ID).size > 2) {
                        val message = MessageToForward(it.uuid, it.text, it.sender, Date(), it.dialog)
                        db.messages += message
                    }
                }
                is BeaconMessage -> processIncomingBeacon(it)
                is BeaconResponse -> processBeaconResponse(it)
            }
        }
    }

    private val sender = PhaseShiftKeyingTransmitter(channelLock)

    private val clients = ArrayList<Messenger>()

    private val messenger = Messenger(Handler {
        when (it.what) {
            MSG_REGISTER_CLIENT -> {
                clients += it.replyTo
            }
            MSG_UNREGISTER_CLIENT -> {
                clients -= it.replyTo
            }
            MSG_SEND_MESSAGE -> {
                handleSendMessage(it.obj!! as String)
            }
            MSG_RECEIVER_PAUSE -> {
                processReceiverEvent(PauseRecording)
            }
            MSG_RECEIVER_RESUME -> {
                processReceiverEvent(StartRecording)
            }
            MSG_RECEIVER_LISTEN_FOR_CALIB -> {
                receiver.listenForCalibration()
            }
            MSG_CHANGE_FREQUENCY -> {
                processReceiverEvent(FrequencyChange(it.obj!! as Double))
            }
            MSG_RECEIVER_EVENT -> {
                processReceiverEvent(it.obj!! as ReceiverEvent)
            }
        }
        false
    })

    // Test data
    private val them = User(UUID(0, 1).toString(), "Jane Doe")
    private val dialog = Dialog(UUID(0, 0).toString(), "Example")

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notify(this, "Batnet is running", 1))

        d("SignalProcessingService", "started")

        thread {
            db.users += them
            db.dialogs += dialog
        }
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handleSendMessage(id: String) {
        thread(name = "Message sender thread") {
            val message = db.messages[id]!!
            //sender.playSound(message.content)
            sender.playMessage(message.ID, message.author, message.content, message.dialog)
        }
    }

    private fun processReceiverEvent(event: ReceiverEvent) {
        receiver.processEvent(event)
        when (event) {
            is FrequencyChange -> sender.frequency = event.newFrequency
            is SymbolLengthChange -> sender.changeSymbolLength(event.samplesPerSymbol, event.windowSize)
        }
    }

    @SuppressLint("UseSparseArrays")
    val indexScores = HashMap<Int, Double>()

    init {
//        /*
        fixedRateTimer(name = "Beacon timer", initialDelay = 120000, period = 60000) {
            if (!receiver.isReceivingSignal) {
                d("Beacon", "Sendig Beacon")
                sender.sendBeacon()
            }
        }
//         */
    }

    private fun processIncomingBeacon(beaconMessage: BeaconMessage) {
        val totalAmplitude = beaconMessage.frequencies.values.sum()
        val suitableFrequencies = beaconMessage.frequencies.filter { (_, v) -> v > totalAmplitude / 8 }.keys

        d("Beacon", "got beacon, preferred frequencies: ${suitableFrequencies.joinToString(",")}; total amp: $totalAmplitude, amplitudes: ${beaconMessage.frequencies.values.joinToString(",")}")
        if (suitableFrequencies.isEmpty()) {
            return // no preference for frequency
        }

        // TODO wait a bit

        sender.sendBeaconResponse(suitableFrequencies)

    }

    private fun processBeaconResponse(beaconResponse: BeaconResponse) {
        d("Beacon", "found beacon response")
        val allIndices = indexScores.keys.toList()
        allIndices.forEach { indexScores[it] = indexScores[it]!!*0.9 }

        beaconResponse.frequencies.forEach {
            indexScores[it] = indexScores[it]?.plus(1) ?: 1.0
        }

        var bestIndex = beaconResponse.frequencies[0]
        beaconResponse.frequencies.forEach {
            if (indexScores[it]!! > indexScores[bestIndex]!!) {
                bestIndex = it
            }
        }

        sender.setFrequencyIndex(bestIndex)

        forwardMessages()
    }

    private fun forwardMessages() {
        db.messages.removeOldMessagesToForward(Date(currentTimeMillis() - 60*60*1000))
        val messagesToForward = db.messages.toForward
        val n = messagesToForward.size

        fun playRandomMessages(times: Int) {
            if (times == 0) return
            val j = nextInt(n)
            val msg = messagesToForward[j]

            sender.playMessage(msg.ID, msg.author, msg.content, msg.dialog) {
                sleep(1000)
                playRandomMessages(times - 1)
            }
        }
        playRandomMessages(20)

    }
}



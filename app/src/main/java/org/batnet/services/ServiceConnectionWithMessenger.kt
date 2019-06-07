package org.batnet.services

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Message.obtain
import android.os.Messenger
import android.util.Log.d

interface WithMessenger {
    val messenger: Messenger?
}

class ServiceConnectionWithMessenger(val onConnected: (Messenger) -> Unit = {}) : ServiceConnection, WithMessenger {

    override var messenger: Messenger? = null
        private set

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val msgr = Messenger(service)
        d("Service connected", name.toString())
        messenger = msgr
        onConnected(msgr)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        messenger = null
    }
}

fun ContextWrapper.bindServiceWithMessageHandler(cls: Class<*>, registerWhat: Int, handler: (Message) -> Boolean): WithMessenger {
    val serviceConnection = ServiceConnectionWithMessenger {
        val msg = obtain(null, registerWhat)
        msg.replyTo = Messenger(Handler(handler))
        it.send(msg)
    }
    bindService(
            Intent(this, cls),
            serviceConnection,
            0
    )
    return serviceConnection
}

fun ContextWrapper.bindSignalProcessingService(handler: (Message) -> Boolean): WithMessenger =
        bindServiceWithMessageHandler(SignalProcessingService::class.java, MSG_REGISTER_CLIENT, handler)

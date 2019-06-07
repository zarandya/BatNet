@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.batnet.services

import android.util.Log.d

interface ITransmitterChannelLock {
    fun startTransmitting()
    fun finishTransmitting()
}

class ChannelLock: java.lang.Object(), ITransmitterChannelLock {

    var isReceiving = false
    var isTransmitting = false
    var isQueuingToTransmit = false
    var isPausedManually = false

    val state get() = "${if (isReceiving) "RECEIVING" else ""}|${when {isTransmitting -> "TRANSMITTING"; isQueuingToTransmit -> "WAITING_TO_TRANSMIT" else -> ""}}"

    @Synchronized
    override fun startTransmitting() {
        d("ChannelLock", "Start transmitting, current state=$state")
        while (isReceiving || isTransmitting || isQueuingToTransmit || isPausedManually) {
             wait()
        }
        isQueuingToTransmit = true
        d("ChannelLock", "Start transmitting, setting state to $state")
        while (!isTransmitting) { // receiver will set this, ok to transmit
            wait()
        }
        isQueuingToTransmit = false
        d("ChannelLock", "Actually Start transmitting, current state=$state")
    }

    @Synchronized
    override fun finishTransmitting() {
        d("ChannelLock", "Finish transmitting, current state=$state")
        isTransmitting = false
        notifyAll()
    }

    inline fun waitUntilCanReadSignal(pauseEvent: () -> Unit, startEvent: () -> Unit) {
        synchronized(this) {
            if (isQueuingToTransmit) {
                pauseEvent()
                isTransmitting = true
                notifyAll()
                while (isTransmitting) {
                    wait()
                }
                startEvent()
            }
            else if (isPausedManually) {
                pauseEvent()
                while (isPausedManually) {
                    wait()
                }
                startEvent()
            }
        }
    }

    @Synchronized
    fun startReceiving() {
        d("ChannelLock", "Start Receiving, current state=$state")
        if (isTransmitting) return // we already started transmitting and this is the last buffer
        isReceiving = true
    }

    @Synchronized
    fun finishReceiving() {
        d("ChannelLock", "Finish Receiving, current state=$state")
        isReceiving = false
        notifyAll()
    }

    @Synchronized
    fun manuallyPauseRecording() {
        d("ChannelLock", "Pause Manually, current state=$state")
        while (isTransmitting || isQueuingToTransmit || isReceiving) {
            wait()
        }
        isPausedManually = true
    }

    @Synchronized
    fun manuallyStartRecording() {
        d("ChannelLock", "Start Manually, current state=$state")
        if (!isPausedManually) return
        isPausedManually = false
        notifyAll()
    }

}


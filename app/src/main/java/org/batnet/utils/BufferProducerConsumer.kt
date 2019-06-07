@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package org.batnet.utils

import java.util.concurrent.LinkedBlockingQueue

class BufferProducerConsumer<T, E>(private val initialiser: () -> T) {

    sealed class DataOrEvent<T, E> {
        data class Data<T, E>(val data: T) : DataOrEvent<T, E>()
        data class Event<T, E>(val event: E) : DataOrEvent<T, E>()
    }

    private val usedItems = LinkedBlockingQueue<DataOrEvent<T, E>>()
    private val unusedResources = LinkedBlockingQueue<T>()

    fun getResource(): T =
        unusedResources.poll() ?: initialiser()

    fun putUnusedResource(unused: T) {
        unusedResources += unused
    }

    fun putResource(resource: T) {
        usedItems += DataOrEvent.Data(resource)
    }

    fun addEvent(event: E) {
        usedItems += DataOrEvent.Event(event)
    }

    inline fun produce(f: (resource: T) -> Unit) {
        val resource = getResource()
        f(resource)
        putResource(resource)
    }

    fun consume(eventHandler: (E) -> Unit = {}): T {
        while (true) {
            val i = usedItems.take()
            when (i) {
                is DataOrEvent.Data -> return i.data
                is DataOrEvent.Event -> eventHandler(i.event)
            }
        }
    }

    fun hasNext() = synchronized(usedItems) {
        usedItems.isNotEmpty()
    }

}
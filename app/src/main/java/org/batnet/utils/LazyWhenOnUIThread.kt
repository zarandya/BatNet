package org.batnet.utils

import java.lang.IllegalStateException
import kotlin.concurrent.thread

class LazyWhenOnUIThread<T>(
        private val initialiser: () -> T,
        private val defaultValue : (IllegalStateException) -> T
) : Lazy<T> {

    constructor(initialiser: () -> T): this(initialiser, { throw it })

    private var valu: T? = null
    private var initialised = false

    init {
        try {
            valu = initialiser()
            initialised = true
        }
        catch (_: IllegalStateException) {
            thread {
                synchronized(this) {
                    if (!initialised) {
                        valu = initialiser()
                        initialised = true
                    }
                }
            }
        }
    }

    override val value: T
        get() =
            try {
                synchronized(this) {
                    if (!initialised) {
                        valu = initialiser()
                        initialised = true
                    }
                    valu!!
                }
            }
            catch (e: IllegalStateException) {
                defaultValue(e)
            }

    override fun isInitialized(): Boolean = initialised
}

fun <T> lazyListWhenOnUIThread(initialiser: () -> List<T>): Lazy<List<T>> =
        LazyWhenOnUIThread(initialiser, { emptyList() })
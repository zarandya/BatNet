package org.batnet

import android.os.Build
import org.junit.Test

import org.junit.Assert.*
import kotlin.concurrent.thread


/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Volatile var f = false

    @Test
    fun concurrency() {
        val a = java.lang.Object()

        thread {
            println("A")
            synchronized(a) {
                println("b")
                f = true
                a.wait()
                println("c")
                f = false
            }
        }

        while (!f) {}

        println("d")
        synchronized(a) {
            println("e")
            a.notifyAll()
            println("f")
        }

        while (f) {}
    }
}

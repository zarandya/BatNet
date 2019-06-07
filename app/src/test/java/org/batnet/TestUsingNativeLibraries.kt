package org.batnet

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.random.Random

abstract class TestUsingNativeLibraries {

    companion object {

        val buildRandom = Random.nextLong()

        private fun buildNativeLibrary(name: String) {
            val p = Runtime.getRuntime().exec("gcc -shared -fPIC -DJUNITTEST -lfftw3 -DJUNIT_BUILD_RANDOM=$buildRandom src/main/cpp/$name.c -I/home/zarandy/.android/ndk-bundle/sysroot/usr/include -o build/lib$name.so")
            BufferedReader(InputStreamReader(p.inputStream)).readLines().forEach {println(it)}
            BufferedReader(InputStreamReader(p.errorStream)).readLines().forEach {println(it)}
            val ex = p.waitFor()
            if (ex != 0) {
                throw RuntimeException("native library $name failed to compile")
            }
            println("Built lib$name.so")
        }

        init {
            println("Build random: $buildRandom")
            buildNativeLibrary("signal-processing")
            buildNativeLibrary("receiver-calibration")
            println(System.getProperty("java.library.path"))
        }
    }
}
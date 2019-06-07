@file:Suppress("NOTHING_TO_INLINE")

package org.batnet.utils

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Complex(val x: Double, val y: Double = 0.0)

val zeroComplex = Complex(0.0, 0.0)
val oneComplex = Complex(1.0, 0.0)

fun polar(m: Double, theta: Double) = Complex(m * cos(theta), m * sin(theta))

operator fun Complex.plus(other: Complex) = Complex(x + other.x, y + other.y)
operator fun Complex.minus(other: Complex) = Complex(x - other.x, y - other.y)
operator fun Complex.times(other: Complex) = Complex(x * other.x - y * other.y, x * other.y + y * other.x)
operator fun Complex.times(other: Double) = Complex(x * other, y * other)

inline fun Complex.sqrMagnitude() = x * x + y * y
inline fun Complex.magnitude() = sqrt(x * x + y * y)

inline fun Complex.normalised(): Complex {
    val norm = sqrt(sqrMagnitude())
    return Complex(x / norm, y / norm)
}

// Complex conjugate
operator fun Complex.not() = Complex(x, -y)

inline infix fun Complex.dot(other: Complex) = x * other.x + y * other.y
inline infix fun Complex.cross(other: Complex) = x * other.y - y * other.x

fun Complex.angle() = atan2(y, x)

fun Iterable<Complex>.sum(): Complex {
    var x = 0.0
    var y = 0.0
    for (element in this) {
        x += element.x
        y += element.y
    }
    return Complex(x, y)
}


val Complex.arg: Double
    get() = atan2(y, x)

package name.nepavel.pickapic

import kotlin.math.sqrt

@Suppress("UNCHECKED_CAST")
fun <T> Any.castUnsafe(): T = this as T

fun calcCoefficient(base: Int, finishes: Int): Float = base/(1 + sqrt(finishes.toFloat()))
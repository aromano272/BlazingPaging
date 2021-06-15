package com.andreromano.blazingpaging.core


typealias Minutes = Int
typealias Seconds = Long
typealias Millis = Long





fun Seconds.toMillis(): Millis = this * 1000
fun Millis.toSeconds(): Seconds = this / 1000

package com.andreromano.blazingpaging.core

typealias RestaurantId = Long
typealias FirebaseUserId = String

typealias ItemId = Long
typealias ItemCategoryId = Long
typealias OrderId = Long

typealias Price = Double

typealias Minutes = Int
typealias Seconds = Long
typealias Millis = Long





fun Seconds.toMillis(): Millis = this * 1000
fun Millis.toSeconds(): Seconds = this / 1000

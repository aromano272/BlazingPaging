package com.andreromano.blazingpaging.sample.reddit.model

enum class Sort {
    BEST,
    HOT,
    NEW,
    RISING,
    CONTROVERSIAL,
    TOP,
    ;

    fun toApiValue(): String = when (this) {
        BEST -> "best"
        HOT -> "hot"
        NEW -> "new"
        RISING -> "rising"
        CONTROVERSIAL -> "controversial"
        TOP -> "top"
    }
}
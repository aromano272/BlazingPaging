package com.andreromano.blazingpaging.sample.common.core

sealed class CombineResult<out T> {
    object ToBeEmitted : CombineResult<Nothing>()
    data class Emission<out T>(val data: T) : CombineResult<T>()

    fun orNull(): T? = when (this) {
        is ToBeEmitted -> null
        is Emission -> this.data
    }
}
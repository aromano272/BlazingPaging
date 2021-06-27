package com.andreromano.blazingpaging.sample.common.core


sealed class ResultKt<out T> {
    data class Success<out T>(val data: T) : ResultKt<T>()
    data class Failure(val error: ErrorKt) : ResultKt<Nothing>()

    suspend fun <R> mapData(body: suspend (T) -> R): ResultKt<R> = when (this) {
        is Success -> Success(body(data))
        is Failure -> this
    }

    fun toResource(): Resource<T> = when (this) {
        is Success -> Resource.Success(this.data)
        is Failure -> Resource.Failure(this.error)
    }

}
package com.andreromano.blazingpaging.sample.common.core


sealed class Resource<out T> {
    object Loading : Resource<Nothing>()

    data class Success<T>(val data: T) : Resource<T>()

    data class Failure(val error: ErrorKt) : Resource<Nothing>()

    suspend fun <O> mapData(mapper: suspend (T) -> O): Resource<O> = when (this) {
        is Loading -> Loading
        is Success -> Success(mapper(data))
        is Failure -> Failure(error)
    }
}
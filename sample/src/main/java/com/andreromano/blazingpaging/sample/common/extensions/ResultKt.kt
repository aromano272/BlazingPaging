package com.andreromano.blazingpaging.sample.common.extensions

import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import com.andreromano.blazingpaging.sample.common.core.ResultKt

suspend fun <T> ResultKt<T>.toCompletable(): ResultKt<Unit> = this.mapData { Unit }

suspend fun <T> ResultKt<T>.doOnSuccess(body: suspend (T) -> Unit): ResultKt<T> =
    apply { if (this is ResultKt.Success) body(this.data) }

suspend fun <T> ResultKt<T>.doOnFailure(body: suspend (ErrorKt) -> Unit): ResultKt<T> =
    apply { if (this is ResultKt.Failure) body(this.error) }

infix fun <A, B> ResultKt<A>.then(continuation: ResultKt<B>): ResultKt<B> = when (this) {
    is ResultKt.Success -> continuation
    is ResultKt.Failure -> this
}

suspend infix fun <A, B> ResultKt<A>.then(continuation: suspend (A) -> ResultKt<B>): ResultKt<B> = when (this) {
    is ResultKt.Success -> continuation(this.data)
    is ResultKt.Failure -> this
}

fun Throwable.toFailure() = ResultKt.Failure(ErrorKt.Unknown(this))

fun ErrorKt.toFailure() = ResultKt.Failure(this)

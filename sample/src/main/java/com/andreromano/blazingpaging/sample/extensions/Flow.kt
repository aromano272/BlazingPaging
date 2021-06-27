package com.andreromano.blazingpaging.sample.extensions

import com.andreromano.blazingpaging.sample.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.ExperimentalTypeInference


fun <T> Flow<T>.startWithPreEmission(): Flow<CombineResult<T>> =
    mapLatest { CombineResult.Emission(it) as CombineResult<T> }
        .onStart { emit(CombineResult.ToBeEmitted) }

fun <T> Flow<Resource<T>>.filterResourceSuccess(): Flow<T> =
    mapNotNull { if (it is Resource.Success) it.data else null }

fun <T> Flow<Resource<T>>.filterResourceFailure(): Flow<ErrorKt> =
    mapNotNull { if (it is Resource.Failure) it.error else null }

fun <T> Flow<Resource<T>>.filterResourceLoading(): Flow<Unit> =
    mapNotNull { if (it is Resource.Loading) Unit else null }

fun <T> Flow<Resource<T>>.toResourceCompletable(): Flow<Resource<Unit>> =
    mapResourceData { Unit }

fun <X, Y> Flow<Resource<X>>.mapResourceData(body: suspend (X) -> Y): Flow<Resource<Y>> =
    map { it.mapData(body) }

fun <T> Flow<ResultKt<T>>.filterResultSuccess(): Flow<T> =
    mapNotNull { if (it is ResultKt.Success) it.data else null }

fun <T> Flow<ResultKt<T>>.filterResultFailure(): Flow<ErrorKt> =
    mapNotNull { if (it is ResultKt.Failure) it.error else null }

fun <T> Flow<ResultKt<T>>.toResultCompletable(): Flow<ResultKt<Unit>> =
    mapResultData { Unit }

fun <X, Y> Flow<ResultKt<X>>.mapResultData(body: suspend (X) -> Y): Flow<ResultKt<Y>> =
    map { it.mapData(body) }

fun <T> Flow<Resource<T>>.onEachResourceSuccess(body: suspend (Resource.Success<T>) -> Unit): Flow<Resource<T>> =
    onEach { if (it is Resource.Success) body(it) }

fun <T> Flow<Resource<T>>.onEachResourceFailure(body: suspend (Resource.Failure) -> Unit): Flow<Resource<T>> =
    onEach { if (it is Resource.Failure) body(it) }

fun <T> Flow<ResultKt<T>>.onEachResultSuccess(body: suspend (T) -> Unit): Flow<ResultKt<T>> =
    onEach { if (it is ResultKt.Success) body(it.data) }

fun <T> Flow<ResultKt<T>>.onEachResultFailure(body: suspend (ErrorKt) -> Unit): Flow<ResultKt<T>> =
    onEach { if (it is ResultKt.Failure) body(it.error) }

fun <T> Flow<Resource<T>>.toResult(): Flow<ResultKt<T>> =
    mapNotNull {
        when (it) {
            is Resource.Loading -> null
            is Resource.Success -> ResultKt.Success(it.data)
            is Resource.Failure -> ResultKt.Failure(it.error)
        }
    }

private object UNINITIALIZED

fun <S, O> Flow<S>.withLatestFrom(other: Flow<O>, combineFirstEmission: Boolean = true): Flow<Pair<S, O>> =
    withLatestFrom(other, combineFirstEmission) { source, other -> source to other }

@OptIn(ExperimentalTypeInference::class)
fun <S, O, R> Flow<S>.transformWithLatestFrom(
    other: Flow<O>,
    combineFirstEmission: Boolean = true,
    @BuilderInference transform: suspend FlowCollector<R>.(S, O) -> Unit,
): Flow<R> =
    withLatestFrom(other, combineFirstEmission)
        .transformLatest { (source, origin) -> transform(source, origin) }

fun <S, O, R> Flow<S>.withLatestFrom(other: Flow<O>, combineFirstEmission: Boolean = true, transform: suspend (S, O) -> R): Flow<R> = channelFlow {
    coroutineScope {
        val latestSource = AtomicReference<Any>(UNINITIALIZED)
        val latestOther = AtomicReference<Any>(UNINITIALIZED)
        val outerScope = this

        launch {
            try {
                other.collect { other ->
                    val prevLatestOther = latestOther.get()
                    val latestSourceValue = latestSource.get()

                    latestOther.set(other)

                    if (combineFirstEmission && prevLatestOther == UNINITIALIZED && latestSourceValue != UNINITIALIZED) {
                        send(transform(latestSourceValue as S, other))
                    }
                }
            } catch (e: CancellationException) {
                outerScope.cancel(e) // cancel outer scope on cancellation exception, too
            }
        }

        collect { source: S ->
            latestSource.set(source)

            latestOther.get().let {
                if (it != UNINITIALIZED) {
                    send(transform(source, it as O))
                }
            }
        }
    }
}

suspend fun <T> Flow<Resource<T>>.firstResult(): ResultKt<T> = toResult().first()

//fun <T> Flow<T>.shareHere(viewModel: ViewModel): SharedFlow<T> = this.shareIn(viewModel.viewModelScope, SharingStarted.Lazily, 1)

//fun <T> Flow<Resource<List<T>>>.asListState() =
//    this.mapLatest { it.toListState() }

val <T> SharedFlow<T>.value
    get() = replayCache.first()

var <T> MutableSharedFlow<T>.value
    get() = replayCache.first()
    set(value) {
        this.tryEmit(value)
    }

fun <T> Flow<T>.conditionalDebounce(
    timeout: Millis = 1000L,
    shouldDebounce: (T) -> Boolean,
): Flow<T> = flatMapLatest {
    if (shouldDebounce(it))
        debounce(timeout)
    else
        flowOf(it)
}

fun <T> Flow<T>.takeUntil(signal: Flow<*>): Flow<T> = flow {
    try {
        coroutineScope {
            launch {
                signal.take(1).collect()
                println("signalled")
                this@coroutineScope.cancel()
            }

            collect {
                emit(it)
            }
        }
    } catch (e: CancellationException) {
        //ignore
    }
}

@Suppress("FunctionName")
fun <T> ActionFlow() = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
@Suppress("FunctionName")
fun <T> ActionFlow(initial: T) = MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST).apply {
    tryEmit(initial)
}

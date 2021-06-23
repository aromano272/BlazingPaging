package com.andreromano.blazingpaging.extensions

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.experimental.ExperimentalTypeInference



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
    timeoutMs: Long = 1000L,
    shouldDebounce: (T) -> Boolean,
): Flow<T> = flatMapLatest {
    if (shouldDebounce(it))
        debounce(timeoutMs)
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

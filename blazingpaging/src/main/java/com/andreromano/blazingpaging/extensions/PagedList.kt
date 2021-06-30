package com.andreromano.blazingpaging.extensions

import com.andreromano.blazingpaging.Page
import com.andreromano.blazingpaging.PageEvent
import com.andreromano.blazingpaging.PagedList
import kotlinx.coroutines.flow.map


fun <T : Any, E, R : Any> PagedList<T, E>.map(transform: (T) -> R): PagedList<R, E> =
    PagedList<R, E>(
        coroutineScope,
        pageEvents.map { it.map(transform) },
        state,
        fetchNextPage,
        retry,
        pageSize,
        prefetchDistance
    )

fun <T : Any, E> PagedList<T, E>.filter(predicate: (T) -> Boolean): PagedList<T, E> =
    PagedList<T, E>(
        coroutineScope,
        pageEvents.map { it.filter(predicate) },
        state,
        fetchNextPage,
        retry,
        pageSize,
        prefetchDistance
    )

private fun <T : Any, R : Any> PageEvent<T>.map(transform: (T) -> R): PageEvent<R> = when (this) {
    is PageEvent.PageAppended -> PageEvent.PageAppended(page.map(transform))
    is PageEvent.PagesReplaced -> PageEvent.PagesReplaced(pages.map { it.map(transform) })
}

private fun <T : Any> PageEvent<T>.filter(predicate: (T) -> Boolean): PageEvent<T> = when (this) {
    is PageEvent.PageAppended -> PageEvent.PageAppended(page.filter(predicate))
    is PageEvent.PagesReplaced -> PageEvent.PagesReplaced(pages.map { it.filter(predicate) })
}

private fun <T : Any, R : Any> Page<T>.map(transform: (T) -> R): Page<R> = Page<R>(items.map(transform))

private fun <T : Any> Page<T>.filter(predicate: (T) -> Boolean): Page<T> = Page<T>(items.filter(predicate))

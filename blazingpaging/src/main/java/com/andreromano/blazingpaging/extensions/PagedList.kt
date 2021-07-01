package com.andreromano.blazingpaging.extensions

import com.andreromano.blazingpaging.Page
import com.andreromano.blazingpaging.PageEvent
import com.andreromano.blazingpaging.PagedList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.runningReduce


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

fun <T1 : Any, T2, R : Any, E> PagedList<T1, E>.combine(with: Flow<T2>, transform: (T1, T2) -> R): PagedList<R, E> =
    PagedList<R, E>(
        coroutineScope,
        // @Note: With this operation we're separating the flows, no longer the received pageEvents from Thingamabob will be the same emitted back down to the adapter,
        //        there is a "world" before the scan which contains the true dataSource pageEvents and a "world" downstream which will be manipulated by the combination of the outside flow.
        //        We're in essence creating our own "invalidation" through this outside flow, that we then propagate downstream.
        pageEvents
            // We need a list of all emitted pages to perform the transform on the combined source, we reset this list when a PagesReplaced is emitted as this already contains all the pages
            .scan(emptyList<PageEvent<T1>>()) { acc, curr ->
                when (curr) {
                    // Flow: PagesReplaced -> PageAppended, we keep the PagesReplaced in the acc because it contains the pages before the appended page
                    is PageEvent.PageAppended -> acc + curr
                    is PageEvent.PagesReplaced -> listOf(curr)
                }
            }
            .drop(1) // Drop initial scan value
            .timestamped()
            .combine(with.timestamped()) { (pageEvents, pageEventsTimestamp), (other, otherTimestamp) ->
                if (otherTimestamp > pageEventsTimestamp) {
                    val pages = pageEvents
                        .flatMap {
                            when (it) {
                                is PageEvent.PageAppended -> listOf(it.page)
                                is PageEvent.PagesReplaced -> it.pages
                            }
                        }
                        .map { page ->
                            page.map { t1 -> transform(t1, other) }
                        }
                    // scan: [A1], [A1,A2], COMBINE, [A1,A2,A3], [R(1,2,3)], [R(1,2,3), A4], COMBINE, [R(1,2,3,4)]
                    // PagedList: A1, A2, R(1,2), A3, R(1,2,3), A4, R(1,2,3,4), R(1,2,3,4)

                    // scan: [A1], [A1,A2], COMBINE, [A1,A2,A3], [R(1,2,3)], COMBINE
                    // PagedList: A1, A2, R(1,2), A3, R(1,2,3), R(1,2,3)
                    PageEvent.PagesReplaced<R>(pages)
                } else {
                    // If the combine was triggered by the pageEvent then i don't need the accumulated pageEvents, i just want to relay the latest one downstream
                    pageEvents.last().map { transform(it, other) }
                }
            },
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

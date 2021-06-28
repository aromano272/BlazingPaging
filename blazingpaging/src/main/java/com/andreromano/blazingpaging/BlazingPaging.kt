package com.andreromano.blazingpaging

import android.annotation.SuppressLint
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.extensions.ActionFlow
import com.andreromano.blazingpaging.extensions.value
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber


/** TODO:
 *      Allow DB, this includes adding invalidation, which in turn will need diffing
 *      Allow DB+Network
 *      Add State.DIFFING? Because diffing may take a while
 *      Allow different viewtypes that are not part of the PagedList and are not counted towards the pagination(similar to Epoxy)
 *      AsyncDiffUtil for submitList(pagedList)
 *      Allow of initial fetch to be larger than pageSize
 */


/**
 * Helper class that takes a Flow to signal invalidation
 * Client should implement a simple Dao method just to signal invalidation:
 * ```
 * @Query("SELECT * FROM Entity LIMIT 0")
 * fun getInvalidationTrigger(): Flow<Entity?>
 * ```
 */
abstract class InvalidatableDataSource<Key : Any, Data, ErrorType>(
    override val invalidationTrigger: Flow<*>,
) : DataSource<Key, Data, ErrorType>()

abstract class DataSource<Key : Any, Data, ErrorType> {
    private val _invalidationTrigger = ActionFlow<Unit>()
    internal open val invalidationTrigger: Flow<*> = _invalidationTrigger

    abstract suspend fun fetchPage(key: Key, pageSize: Int): FetchResult<Key, Data, ErrorType>

    sealed class FetchResult<out Key, out Data, out ErrorType> {
        data class Success<Key, out Data>(val nextPageKey: Key?, val data: List<Data>) : FetchResult<Key, Data, Nothing>()
        data class Failure<ErrorType>(val error: ErrorType) : FetchResult<Nothing, Nothing, ErrorType>()
    }
}


// TODO: It doesnt make sense for PagedList to just be a data holder as it currently is, atm the Adapter is the center piece of the pagination
//       and that just feels wrong... Understandably the adapter needs to get a flow of pages so it can attach them, and it needs to hold the nonpaginated
//       version of the data, maybe have this in the PagedList and communicate over listeners?
data class PagedList<T : Any, ErrorType> internal constructor(
    internal val coroutineScope: CoroutineScope,
    val pages: Flow<Page<T>>,
    val state: Flow<Thingamabob.State<ErrorType>>,
    val invalidationTrigger: Flow<*>,
    val fetchNextPage: () -> Unit,
    val retry: () -> Unit,
    internal val pageSize: Int,
    val prefetchDistance: Int,
) : AbstractList<T>() {

    override val size: Int
        get() = throw UnsupportedOperationException()

    private var currentPosition: Int = 0
    override fun get(index: Int): T {
        throw UnsupportedOperationException()
        //        currentPosition = index
        //
        //        if (pagesState.value.size - currentPosition <= threshold) fetchNextPage()
        //
        //        return pagesState.value[index]
    }
}

fun <T : Any, E, R : Any> PagedList<T, E>.map(transform: (T) -> R): PagedList<R, E> =
    PagedList<R, E>(coroutineScope, pages.map { it.map(transform) }, state, invalidationTrigger, fetchNextPage, retry, pageSize, prefetchDistance)

fun <T : Any, E> PagedList<T, E>.filter(predicate: (T) -> Boolean): PagedList<T, E> =
    PagedList<T, E>(coroutineScope, pages.map { it.filter(predicate) }, state, invalidationTrigger, fetchNextPage, retry, pageSize, prefetchDistance)

fun <T : Any, R : Any> Page<T>.map(transform: (T) -> R): Page<R> = Page<R>(items.map(transform))

fun <T : Any> Page<T>.filter(predicate: (T) -> Boolean): Page<T> = Page<T>(items.filter(predicate))


// TODO: Name it
data class Thingamabob<Key : Any, T : Any, ErrorType>(
    private val coroutineScope: CoroutineScope,
    private val config: Config<Key>,
    private val dataSource: DataSource<Key, T, ErrorType>,
) {
    /**
     * @param initialKey Key must not be null, keys which initial value is null should create a wrapper class `data class Key(val key: String?)`
     * @param prefetchDistance Is the minimum amount of items available in front of the current position before fetching a new page, defaults to pageSize * 2
     */
    data class Config<Key>(
        val initialKey: Key,
        val pageSize: Int,
        val prefetchDistance: Int = pageSize * 2,
    )

    init {
        dataSource.invalidationTrigger
            .onEach {
                tryFetchNextPageJob.cancel()
                // We wait for the adapter to request another page after the invalidation, because Thingamabob has no clue what page/s are currently displaying
                _invalidationTrigger.value = Unit
            }
            .launchIn(coroutineScope)
    }

    // This is starting to shape up like the PageEvent's the new paging library uses, we need this to signal the PagedList that a new page was fetched
    // the pagedlist will in turn filter the results if it needs to and it will determine if it should get another page or not,
    // this way all of this logic goes smoothly to the pagedlist side while still separating the logic between these classes
    // ^ Old comment, we're now just using the backingList to always send the full new list and let the differ handle the rest
    private val backingList = ActionFlow<Page<T>>()
    private val state = MutableStateFlow<State<ErrorType>>(State.Idle)
    private val _invalidationTrigger = ActionFlow<Unit>()
    private val invalidationTrigger: Flow<*> = _invalidationTrigger
    private var nextPageKey: Key? = config.initialKey

    fun buildPagedList(): PagedList<T, ErrorType> = PagedList(
        coroutineScope,
        backingList,
        state,
        invalidationTrigger,
        ::tryFetchNextPage,
        ::retry,
        config.pageSize,
        config.prefetchDistance,
    )

    private lateinit var tryFetchNextPageJob: Job
    private fun tryFetchNextPage() {
        if (::tryFetchNextPageJob.isInitialized && !tryFetchNextPageJob.isCompleted || nextPageKey == null || state.value != State.Idle) {
            Timber.d("SHABAM tryFetchNextPage() halted tryFetchNextPageJob.isCompleted: ${::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isCompleted} || hasReachedEnd: ${nextPageKey == null} || state.value != State.Idle: ${state.value != State.Idle}")
            return
        }

        tryFetchNextPageJob = coroutineScope.launch {
            fetchNextPage()
        }
    }

    private suspend fun fetchNextPage() {
        Timber.d("SHABAM fetchNextPage() nextPageKey: $nextPageKey")

        val key = nextPageKey ?: return
        state.value = State.Fetching
        Timber.d("SHABAM getData($key)")

        when (val result = dataSource.fetchPage(key, config.pageSize)) {
            is DataSource.FetchResult.Success -> {
                val data = result.data

                nextPageKey = result.nextPageKey
                // START TODO: These 2 have a race condition, because when we update backingList, PagedList will call tryFetchNextPage if we still need to get new pages
                //             and as the state is still FETCHING it will fail, as it stand it works, but this race condition is ugly. or maybe its just ok since
                //             we now rely on Flow's and backingList is the result flow, in essence how this method communicates with the rest of the system.
                state.value = State.Idle
                backingList.value = Page(data)
                // END

                Timber.d("SHABAM getData success result.size: ${data.size}")
            }
            is DataSource.FetchResult.Failure -> {
                state.value = State.Error(result.error)
                Timber.d("SHABAM getData failure error: ${result.error}")
            }
        }
    }

    private fun retry() {
        if (state.value !is State.Error) return
        state.value = State.Idle
        tryFetchNextPage()
    }

    sealed class State<out ErrorType> {
        object Idle : State<Nothing>()
        object Fetching : State<Nothing>()
        data class Error<ErrorType>(val error: ErrorType) : State<ErrorType>()
    }
}

class Page<T>(val items: List<T>)

abstract class PagedListAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    private val coroutineScope: CoroutineScope,
    diffUtil: DiffUtil.ItemCallback<T>,
) : RecyclerView.Adapter<VH>() {

    private var layoutManager: RecyclerView.LayoutManager? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.layoutManager = recyclerView.layoutManager
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.layoutManager = null
    }

    private var pagedList: PagedList<T, *>? = null

    private var currentList: List<T> = emptyList()

    private fun invalidate() {
        diffingJob.cancel()
        // TODO: Now the decision is, should we replace the currentList with an empty list and call notifyItemRangeRemoved or should we wait for the first page for a diff?
        //       TBH i really want the diffing, and the Thingamabob should also keep the current page, so it fetches the current page, so we can correctly diff it.
        //       This has to be one of the reasons Paging 2 has different DataSources because some datasources keep their pages on invalidation and some must not do so,
        //       seeing that Paging 2 cant navigate back, if the client invalidated a network datasource the system has to start from the beginning.
        //       What i essentially want is for the client to be able to, for example, favorite a listitem, triggering an invalidation, and staying in the same position
        //       in the list, having seen no change other than the fact that now the item is favorited.


        //      TODO: Create togglebutton in the dataentity, so we can simulate invalidation and diffing
        notifyItemRangeRemoved(0, currentList.size)
        currentList = emptyList()
    }

    private lateinit var invalidationJob: Job
    private lateinit var diffingJob: Job
    fun submitList(newList: PagedList<T, *>?) {
        if (pagedList == newList) return
        if (pagedList != null) {
            if (::diffingJob.isInitialized) diffingJob.cancel()
            if (::invalidationJob.isInitialized) invalidationJob.cancel()
            notifyItemRangeRemoved(0, currentList.size)
            currentList = emptyList()
        }

        pagedList = newList

        val pagedList = pagedList ?: return

        diffingJob = pagedList.pages
            .onEach { newPage ->
//                Timber.i("SHABAM Adapter diffingJob.onEach newPage: $newPage currentList: $currentList")
                val oldList = currentList
                currentList = currentList + newPage.items
                if (currentList.size - currentPosition < pagedList.prefetchDistance) pagedList.fetchNextPage()
                notifyItemRangeInserted(oldList.size, newPage.items.size)
            }
            .launchIn(coroutineScope)

        invalidationJob = pagedList.invalidationTrigger
            .onEach {
                // TODO: Do we have to cancel the current diffing? atm we're not diffing and the pagedList.pages job is being canceled in the thingamabob

                // TODO: This adapter doesnt have a way to let the paging system know which page is requires
                val visibleRange = getVisibleItemRange()
                pagedList.fetchNextPage(visibleRange)
            }
            .launchIn(coroutineScope)

        pagedList.fetchNextPage()
    }

    private var currentPosition: Int = 0
    protected fun getItem(position: Int): T {
        currentPosition = position
        pagedList?.let { pagedList ->
            if (currentList.size - currentPosition < pagedList.prefetchDistance) pagedList.fetchNextPage()
        }
        val item = currentList[position]
//        Timber.i("SHABAM Adapter getItem($position) = $item differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return item
    }

    override fun getItemCount(): Int {
//        Timber.i("SHABAM Adapter getItemCount() = ${currentList.size} differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return currentList.size
    }

    private fun getVisibleItemRange(): IntRange =
        when (val layoutManager = layoutManager) {
            is LinearLayoutManager -> {
                val firstItem = layoutManager.findFirstVisibleItemPosition()
                val lastItem = layoutManager.findLastVisibleItemPosition()

                val visibleRange =
                    if (firstItem == -1 || lastItem == -1) 0 until 0
                    else firstItem..lastItem

                visibleRange
            }
            is StaggeredGridLayoutManager -> {
                val firstArray = IntArray(3)
                val lastArray = IntArray(3)
                layoutManager.findFirstVisibleItemPositions(firstArray)
                layoutManager.findLastVisibleItemPositions(lastArray)

                val firstItem = firstArray.filterNot { it == -1 }.minOrNull()
                val lastItem = lastArray.filterNot { it == -1 }.maxOrNull()
                val visibleRange =
                    if (firstItem == null || lastItem == null) 0 until 0
                    else firstItem..lastItem

                visibleRange
            }
            else -> throw UnsupportedOperationException()
        }

}

class DebugListUpdateCallback(private val adapter: RecyclerView.Adapter<*>) : ListUpdateCallback {
    override fun onInserted(position: Int, count: Int) {
        Timber.e("SHABAM DiffUtil onInserted(position: $position, count: $count)")
        adapter.notifyItemRangeInserted(position, count)
    }

    override fun onRemoved(position: Int, count: Int) {
        Timber.e("SHABAM DiffUtil onRemoved(position: $position, count: $count)")
        adapter.notifyItemRangeRemoved(position, count)
    }

    override fun onMoved(fromPosition: Int, toPosition: Int) {
        Timber.e("SHABAM DiffUtil onMoved(fromPosition: $fromPosition, toPosition: $toPosition)")
        adapter.notifyItemMoved(fromPosition, toPosition)
    }

    override fun onChanged(position: Int, count: Int, payload: Any?) {
        Timber.e("SHABAM DiffUtil onChanged(position: $position, count: $count, payload: $payload)")
        adapter.notifyItemRangeChanged(position, count, payload)
    }
}

//class AsyncPagedListDiffer<T : Any>(
//    private val coroutineScope: CoroutineScope,
//    private val adapter: RecyclerView.Adapter<*>,
//    private val diffCallback: DiffUtil.ItemCallback<T>,
//    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
//    private val diffingDispatcher: CoroutineDispatcher = Dispatchers.Default,
//) {
//
//    private val listUpdateCallback = DebugListUpdateCallback(adapter)
//
//    private val currentPagedList = MutableStateFlow<PagedList<T>?>(null)
//
//    init { currentPagedList.map { Timber.i("SHABAM currentPagedList: $it ${System.identityHashCode(it)}") }.launchIn(coroutineScope) }
//
//    private val currentBackingList: StateFlow<List<T>?> =
//        currentPagedList
//            .flatMapLatest { it?.pages ?: flowOf(null) }
//            .stateIn(coroutineScope, SharingStarted.Lazily, null)
//    init { currentBackingList.map { Timber.i("SHABAM currentBackingList: $it ${System.identityHashCode(it)}") }.launchIn(coroutineScope) }
//
//    // FIXME: Exposing the pagedlist for .get and .size, and having the differ based on the currentBackingList StateFlow, might be a real problem because
//    //        currentPagedList and currentBackingList might get unsynced, im not sure if currentPagedList will wait for currentBackingList and if currentBackingList
//    //        will even wait for the diff, most likely not.... this is gonna be a real problem
//    fun snapshot() = currentPagedList.value
//
//    init {
//        currentBackingList
//            .scan<List<T>?, Pair<List<T>?, List<T>?>>(null to null) { (_, old), new ->
//                old to new
//            }
//            .map { (old, new) -> differ(old, new) }
//            .flowOn(diffingDispatcher)
//            .onEach { diffResult -> diffResult?.dispatchUpdatesTo(listUpdateCallback) }
//            .onEach { Timber.i("SHABAM diffResult: $it ${System.identityHashCode(it)}") }
//            .flowOn(mainDispatcher)
//            .launchIn(coroutineScope)
//    }
//
//
//    private fun differ(currentSnapshot: List<T>?, newSnapshot: List<T>?): DiffUtil.DiffResult? {
//        Timber.d("SHABAM differ current: ${currentSnapshot?.size} new: ${newSnapshot?.size}")
//        if (currentSnapshot == null && newSnapshot == null) return null
//        if (currentSnapshot == null && newSnapshot != null) {
//            listUpdateCallback.onInserted(0, newSnapshot.size)
//            return null
//        }
//        if (currentSnapshot != null && newSnapshot == null) {
//            listUpdateCallback.onRemoved(0, currentSnapshot.size)
//            return null
//        }
//        require(currentSnapshot != null)
//        require(newSnapshot != null)
//
//        return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
//            override fun getOldListSize(): Int = currentSnapshot.size
//            override fun getNewListSize(): Int = newSnapshot.size
//            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
//                diffCallback.areItemsTheSame(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])
//
//            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
//                diffCallback.areContentsTheSame(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])
//
//            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
//                diffCallback.getChangePayload(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])
//        })
//    }
//
//    fun submitList(pagedList: PagedList<T>?) {
//        currentPagedList.value = pagedList
//    }
//
//}

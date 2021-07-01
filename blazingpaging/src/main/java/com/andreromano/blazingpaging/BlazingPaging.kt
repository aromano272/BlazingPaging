package com.andreromano.blazingpaging

import android.os.Looper
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.extensions.ActionFlow
import com.andreromano.blazingpaging.extensions.value
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber


/** TODO:
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
abstract class DatabaseDataSource<Key : Any, Data, ErrorType>(
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
    val pageEvents: Flow<PageEvent<T>>,
    val state: Flow<Thingamabob.State<ErrorType>>,
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

    // TODO: While we're fetching the replacement pages after invalidation we stop the pagination all together,
    //       we're just assuming this comes from the database so it should be quick
    private var isInvalidated = false

    init {
        dataSource.invalidationTrigger
            .drop(1)
            .onEach {
                Timber.d("SHABAM invalidationTrigger")
                require(dataSource is DatabaseDataSource)
                isInvalidated = true
                if (::tryFetchNextPageJob.isInitialized) tryFetchNextPageJob.cancel()
                tryFetchNextPageJob = coroutineScope.launch {
                    fetchPagesAfterInvalidation()
                }
            }
            .launchIn(coroutineScope)
    }

    private val pageEvents = ActionFlow<PageEvent<T>>()
    private var fetchedKeysSinceInvalidation = emptyList<Key>()
    private val state = MutableStateFlow<State<ErrorType>>(State.Idle)
    private var nextPageKey: Key? = config.initialKey

    fun buildPagedList(): PagedList<T, ErrorType> = PagedList(
        coroutineScope,
        pageEvents,
        state,
        ::tryFetchNextPage,
        ::retry,
        config.pageSize,
        config.prefetchDistance,
    )

    private lateinit var tryFetchNextPageJob: Job
    private fun tryFetchNextPage() {
        val nextPageKey = nextPageKey
        if (::tryFetchNextPageJob.isInitialized && !tryFetchNextPageJob.isCompleted || nextPageKey == null || state.value != State.Idle || isInvalidated) {
            Timber.d("SHABAM tryFetchNextPage() halted tryFetchNextPageJob.isCompleted: ${::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isCompleted} || hasReachedEnd: ${nextPageKey == null} || state.value != State.Idle: ${state.value != State.Idle} || isInvalidated: $isInvalidated")
            return
        }

        tryFetchNextPageJob = coroutineScope.launch {
            fetchPage(nextPageKey)
        }
    }

    private suspend fun fetchPage(key: Key) {
        Timber.d("SHABAM fetchPage($key)")

        state.value = State.Fetching
        Timber.d("SHABAM getData($key)")

        when (val result = dataSource.fetchPage(key, config.pageSize)) {
            is DataSource.FetchResult.Success -> {
                val data = result.data

                nextPageKey = result.nextPageKey
                state.value = State.Idle
                pageEvents.value = PageEvent.PageAppended(Page(data))
                fetchedKeysSinceInvalidation = fetchedKeysSinceInvalidation + key

                Timber.d("SHABAM getData success result.size: ${data.size}")
            }
            is DataSource.FetchResult.Failure -> {
                state.value = State.Error(result.error)
                Timber.d("SHABAM getData failure error: ${result.error}")
            }
        }
    }

    private suspend fun fetchPagesAfterInvalidation() {
        val keys = fetchedKeysSinceInvalidation
        Timber.d("SHABAM fetchPages($keys)")
        state.value = State.Fetching
        val newPages = mutableListOf<Page<T>>()
        var newNextPageKey: Key? = null

        keys.forEach { key ->
            Timber.d("SHABAM getData($key)")

            when (val result = dataSource.fetchPage(key, config.pageSize)) {
                is DataSource.FetchResult.Success -> {
                    newPages += Page(result.data)
                    newNextPageKey = result.nextPageKey
                    Timber.d("SHABAM getData success result.size: ${result.data.size}")
                }
                is DataSource.FetchResult.Failure -> {
                    throw IllegalStateException("Invalidation is only supported in DatabaseDataSources, and those should not be failing")
                }
            }
        }

        isInvalidated = false
        pageEvents.value = PageEvent.PagesReplaced(newPages)
        nextPageKey = newNextPageKey

        state.value = State.Idle
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

data class Page<T>(
//    val key: Key,
    val items: List<T>,
)

sealed class PageEvent<T> {
    data class PageAppended<T>(val page: Page<T>) : PageEvent<T>()
    data class PagesReplaced<T>(val pages: List<Page<T>>) : PageEvent<T>()
}

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

    private val differ = AsyncListDiffer<T>(
        DebugListUpdateCallback(this),
        AsyncDifferConfig.Builder(diffUtil).build()
    )


    private lateinit var invalidationJob: Job
    private lateinit var diffingJob: Job
    fun submitList(newList: PagedList<T, *>?) {
        if (pagedList == newList) return
        if (pagedList != null) {
            if (::diffingJob.isInitialized) diffingJob.cancel()
            if (::invalidationJob.isInitialized) invalidationJob.cancel()
            differ.submitList(emptyList())
        }

        pagedList = newList

        val pagedList = pagedList ?: return

        diffingJob = pagedList.pageEvents
            .onEach { pageEvent ->
                when (pageEvent) {
                    is PageEvent.PageAppended -> {
                        Timber.d("SHABAM PageEvent.PageAppended mainThread: ${Looper.myLooper() == Looper.getMainLooper()}")
                        val newList = differ.currentList + pageEvent.page.items
                        differ.submitList(newList) {
                            if (newList.size - currentPosition < pagedList.prefetchDistance) pagedList.fetchNextPage()
                        }
                    }
                    is PageEvent.PagesReplaced -> {
                        Timber.d("SHABAM PageEvent.PagesReplaced mainThread: ${Looper.myLooper() == Looper.getMainLooper()}")
                        val newPages = pageEvent.pages
                        val newList = newPages.flatMap { it.items }

                        differ.submitList(newList)
                        return@onEach
                    }
                }
            }
            .flowOn(Dispatchers.Main)
            .launchIn(coroutineScope)

        pagedList.fetchNextPage()
    }

    private var currentPosition: Int = 0
    protected fun getItem(position: Int): T {
        currentPosition = position
        pagedList?.let { pagedList ->
            if (differ.currentList.size - currentPosition < pagedList.prefetchDistance) pagedList.fetchNextPage()
        }
        val item = differ.currentList[position]
//        Timber.i("SHABAM Adapter getItem($position) = $item differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return item
    }

    override fun getItemCount(): Int {
//        Timber.i("SHABAM Adapter getItemCount() = ${currentList.size} differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return differ.currentList.size
    }


//    private fun getVisiblePages(): List<Page<T>> {
//        val visibleRange = getVisibleItemRange()
//
//        val visiblePages = mutableListOf<Page<T>>()
//
//        var lastSeenRange: IntRange? = null
//        currentPages.forEach { page ->
//            val pageSize = page.items.size
//            val start = lastSeenRange?.first?.plus(pageSize) ?: 0
//            val end = lastSeenRange?.last?.plus(pageSize) ?: pageSize
//            val range = start until end
//
//            if (visibleRange.first in range) {
//                visiblePages += page
//            }
//
//            lastSeenRange = range
//        }
//
//        return visiblePages
//    }
//
//    private fun getVisibleItemRange(): IntRange =
//        when (val layoutManager = layoutManager) {
//            is LinearLayoutManager -> {
//                val firstItem = layoutManager.findFirstVisibleItemPosition()
//                val lastItem = layoutManager.findLastVisibleItemPosition()
//
//                val visibleRange =
//                    if (firstItem == -1 || lastItem == -1) 0 until 0
//                    else firstItem..lastItem
//
//                visibleRange
//            }
//            is StaggeredGridLayoutManager -> {
//                val firstArray = IntArray(3)
//                val lastArray = IntArray(3)
//                layoutManager.findFirstVisibleItemPositions(firstArray)
//                layoutManager.findLastVisibleItemPositions(lastArray)
//
//                val firstItem = firstArray.filterNot { it == -1 }.minOrNull()
//                val lastItem = lastArray.filterNot { it == -1 }.maxOrNull()
//                val visibleRange =
//                    if (firstItem == null || lastItem == null) 0 until 0
//                    else firstItem..lastItem
//
//                visibleRange
//            }
//            else -> throw UnsupportedOperationException()
//        }

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

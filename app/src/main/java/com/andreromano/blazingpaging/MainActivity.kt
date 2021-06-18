package com.andreromano.blazingpaging

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.extensions.ActionFlow
import com.andreromano.blazingpaging.extensions.value
import com.andreromano.blazingpaging.other.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber


/** TODO:
 *      Fix bug where if all results are filtered or diffed out, PagedList.get() won't get called, thus not triggering fetchNextPage()
 *      Implement PagedList.map
 *      Prefetch distance
 *      Headers/Footers
 *      Allow different viewtypes that are not part of the PagedList and are not counted towards the pagination(similar to Epoxy)
 *      Allow DB+Network
 *      AsyncDiffUtil for submitList(pagedList)
 *
 */
class MainActivity : AppCompatActivity() {

    private val dataAdapter by lazy {
        DataPagedListAdapter(lifecycleScope)
    }

    private val pagedListFlow = ActionFlow<PagedList<Data>>()

    private val pagedListFlowState = pagedListFlow.flatMapLatest { it.state }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = dataAdapter

        sw_switch.isChecked = !Api.shouldFail
        sw_switch.setOnCheckedChangeListener { _, isChecked -> Api.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
//            pagedListFlow.value.retry()
        }
        fab.setOnClickListener {
//            pagedListFlow.value = PagingSource(lifecycleScope,
//                10,
//                CustomDataSource(Repository::getDiffUtilData)) //.map { it.copy(name = "${it.name} aljsdaj") }.filter { it.id > 20 }
        }

        lifecycleScope.launchWhenCreated {
            pagedListFlow.collect {
                dataAdapter.submitList(it)
            }
        }

        lifecycleScope.launchWhenCreated {
            pagedListFlowState.collect {
                tv_title.text = it.name
                btn_retry.visibility = if (it == Thingamabob.State.ERROR) View.VISIBLE else View.GONE
            }
        }

//        setupBug_1()
        setupBug_1_solution()
    }

    // if all results are filtered or diffed out, PagedList.get() won't get called, thus not triggering fetchNextPage()
    private fun setupBug_1() {
//        pagedListFlow.value = PagingSource(lifecycleScope, 10, CustomDataSource(Repository::getSequentialData)).filter { it.id > 20 }
    }

    private fun setupBug_1_solution() {
        pagedListFlow.value = Thingamabob<Data>(lifecycleScope, 10, CustomDataSource(Repository::getSequentialData)).buildPagedList().filter { it.id > 30 }
    }
}


abstract class DataSource<T : Any> {
    abstract suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T>

    sealed class FetchResult<out T> {
        data class Success<out T>(val data: List<T>, val hasReachedEnd: Boolean) : FetchResult<T>()
        data class Failure(val error: ErrorKt) : FetchResult<Nothing>()
    }
}

data class PagedList<T : Any>(
    internal val coroutineScope: CoroutineScope,
    val backingList: Flow<List<T>>,
    val state: Flow<Thingamabob.State>,
    internal val fetchNextPage: () -> Unit,
    internal val pageSize: Int,
) : AbstractList<T>() {

    private val threshold = pageSize

    // TODO: backingList.stateIn, this seems sketchy to have to keep this, why do we expose pages and also want .get() and size to be called from here?
    private val pagesState = backingList.stateIn(coroutineScope, SharingStarted.Lazily, emptyList())

    init {
        backingList
            .onEach {
                if (it.size - currentPosition <= threshold) fetchNextPage()
            }
            .launchIn(coroutineScope)

        pagesState
            .onEach {
                Timber.i("SHABAM PagedList pagesState: ${pagesState.value} ${System.identityHashCode(this@PagedList)}")
            }
            .launchIn(coroutineScope)
    }

    override val size: Int
        get() = pagesState.value.size

    private var currentPosition: Int = 0
    override fun get(index: Int): T {
        currentPosition = index

        if (pagesState.value.size - currentPosition <= threshold) fetchNextPage()

        return pagesState.value[index]
    }

}

fun <T : Any, R : Any> PagedList<T>.map(transform: (T) -> R): PagedList<R> = PagedList<R>(coroutineScope, backingList.map { it.map(transform) }, state, fetchNextPage, pageSize)

fun <T : Any> PagedList<T>.filter(predicate: (T) -> Boolean): PagedList<T> = PagedList<T>(coroutineScope, backingList.map { it.filter(predicate) }, state, fetchNextPage, pageSize)

// TODO: Name it
class Thingamabob<T : Any>(
    private val coroutineScope: CoroutineScope,
    private val pageSize: Int,
    private val dataSource: DataSource<T>,
) {
    // This is starting to shape up like the PageEvent's the new paging library uses, we need this to signal the PagedList that a new page was fetched
    // the pagedlist will in turn filter the results it if needs to and it will determine if it should get another page or not,
    // this way all of this logic goes smoothly to the pagedlist side while still separating the logic between these classes
    // ^ Old comment, we're now just using the backingList to always send the full new list and let the differ handle the rest
    val pageFetched = MutableSharedFlow<Unit>()
    val backingList = ActionFlow<List<T>>(emptyList())
    val state = MutableStateFlow(State.IDLE)
    private var hasReachedEnd = false
    private var currentPage = 0

    fun buildPagedList(): PagedList<T> = PagedList(
        coroutineScope,
        backingList,
        state,
        ::tryFetchNextPage,
        pageSize
    )

    private lateinit var tryFetchNextPageJob: Job
    private fun tryFetchNextPage() {
        if (::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isActive || hasReachedEnd || state.value != State.IDLE) {
            Timber.d("SHABAM tryFetchNextPage() halted tryFetchNextPageJob.isActive: ${::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isActive} || hasReachedEnd: $hasReachedEnd || state.value != State.IDLE: ${state.value != State.IDLE}")
            return
        }

        coroutineScope.launch {
            fetchNextPage()
        }
    }

    private suspend fun fetchNextPage() {
        Timber.d("SHABAM fetchNextPage() currentPage: $currentPage")

        val page = currentPage + 1
        state.value = State.FETCHING
        Timber.d("SHABAM getData($page)")

        when (val result = dataSource.fetchPage(page, pageSize)) {
            is DataSource.FetchResult.Success -> {
                val data = result.data

                currentPage++
                // START TODO: These 2 have a race condition, because when we update backingList, PagedList will call tryFetchNextPage if we still need to get new pages
                //             and as the state is still FETCHING it will fail, as it stand it works, but this race condition is ugly. or maybe its just ok since
                //             we now rely on Flow's and backingList is the result flow, in essence how this method communicates with the rest of the system.
                state.value = State.IDLE
                hasReachedEnd = result.hasReachedEnd
                backingList.value += data
                // END

                Timber.d("SHABAM getData success result.size: ${data.size}")
            }
            is DataSource.FetchResult.Failure -> {
                state.value = State.ERROR
                Timber.d("SHABAM getData failure error: ${result.error}")
            }
        }
    }

    enum class State {
        IDLE,
        FETCHING,
        ERROR
    }
}

class AsyncPagedListDiffer<T : Any>(
    private val coroutineScope: CoroutineScope,
    private val adapter: RecyclerView.Adapter<*>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val diffingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val listUpdateCallback = DebugListUpdateCallback(adapter)

    private val currentPagedList = MutableStateFlow<PagedList<T>?>(null)
    init { currentPagedList.map { Timber.i("SHABAM currentPagedList: $it ${System.identityHashCode(it)}") }.launchIn(coroutineScope) }
    private val currentBackingList: StateFlow<List<T>?> =
        currentPagedList
            .flatMapLatest { it?.backingList ?: flowOf(null) }
            .stateIn(coroutineScope, SharingStarted.Lazily, null)
    init { currentBackingList.map { Timber.i("SHABAM currentBackingList: $it ${System.identityHashCode(it)}") }.launchIn(coroutineScope) }

    // FIXME: Exposing the pagedlist for .get and .size, and having the differ based on the currentBackingList StateFlow, might be a real problem because
    //        currentPagedList and currentBackingList might get unsynced, im not sure if currentPagedList will wait for currentBackingList and if currentBackingList
    //        will even wait for the diff, most likely not.... this is gonna be a real problem
    fun snapshot() = currentPagedList.value

    init {
        currentBackingList
            .scan<List<T>?, Pair<List<T>?, List<T>?>>(null to null) { (_, old), new ->
                old to new
            }
            .map { (old, new) -> differ(old, new) }
            .flowOn(diffingDispatcher)
            .onEach { diffResult -> diffResult?.dispatchUpdatesTo(listUpdateCallback) }
            .onEach { Timber.i("SHABAM diffResult: $it ${System.identityHashCode(it)}") }
            .flowOn(mainDispatcher)
            .launchIn(coroutineScope)
    }


    private fun differ(currentSnapshot: List<T>?, newSnapshot: List<T>?): DiffUtil.DiffResult? {
        Timber.d("SHABAM differ current: ${currentSnapshot?.size} new: ${newSnapshot?.size}")
        if (currentSnapshot == null && newSnapshot == null) return null
        if (currentSnapshot == null && newSnapshot != null) {
            listUpdateCallback.onInserted(0, newSnapshot.size)
            return null
        }
        if (currentSnapshot != null && newSnapshot == null) {
            listUpdateCallback.onRemoved(0, currentSnapshot.size)
            return null
        }
        require(currentSnapshot != null)
        require(newSnapshot != null)

        return DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = currentSnapshot.size
            override fun getNewListSize(): Int = newSnapshot.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areItemsTheSame(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                diffCallback.areContentsTheSame(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])

            override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
                diffCallback.getChangePayload(currentSnapshot[oldItemPosition], newSnapshot[newItemPosition])
        })
    }

    fun submitList(pagedList: PagedList<T>?) {
        currentPagedList.value = pagedList
    }

}


abstract class PagedListAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    private val coroutineScope: CoroutineScope,
    diffUtil: DiffUtil.ItemCallback<T>,
) : RecyclerView.Adapter<VH>() {
    private val differ = AsyncPagedListDiffer<T>(coroutineScope, this, diffUtil)

    fun submitList(newList: PagedList<T>?) {
        differ.submitList(newList)
        // TODO: This should only start if or after being attached to a recyclerview, this .start() is a hack
//        list?.start()
    }

    protected fun getItem(position: Int): T {
        val snapshot = differ.snapshot()
        val item = snapshot?.get(position)!!
        Timber.i("SHABAM Adapter getItem($position) = $item differ.snapshot: $snapshot ${System.identityHashCode(snapshot)}")
        return item
    }

    override fun getItemCount(): Int {
        val snapshot = differ.snapshot()
        val size = snapshot?.size ?: 0
        Timber.i("SHABAM Adapter getItemCount() = $size differ.snapshot: $snapshot ${System.identityHashCode(snapshot)}")
        return size
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


abstract class PagingSourceAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    private val coroutineScope: CoroutineScope,
    diffUtil: DiffUtil.ItemCallback<T>,
) : RecyclerView.Adapter<VH>() {
    private val differ = AsyncPagingSourceDiffer<T>(this, diffUtil)
    private var list: PagingSource<T>? = null

    private val pagedListCallback = object : PagingSource.AdapterCallback<T> {
        override fun pageFetched(page: List<T>) {
            coroutineScope.launch {
                //                differ.pageFetched(page)
                differ.submitList(list)
            }
        }
    }

    suspend fun submitList(newList: PagingSource<T>?) {
        list?.adapterCallback = null
        newList?.adapterCallback = pagedListCallback
        list = newList
        differ.submitList(newList)
        // TODO: This should only start if or after being attached to a recyclerview, this .start() is a hack
        list?.start()
    }

    protected fun getItem(position: Int): T = differ.currentList?.get(position)!!

    override fun getItemCount(): Int = differ.currentList?.getCount() ?: 0
}

class PagingSource<T : Any>(
    private val coroutineScope: CoroutineScope,
    private val pageSize: Int,
    private val dataSource: DataSource<T>,
) {

    private var filterFunc: ((T) -> Boolean)? = null

    internal var adapterCallback: AdapterCallback<T>? = null
    var state: State = State.IDLE
        private set(value) {
            field = value
            _stateFlow.value = value
        }
    private val _stateFlow = MutableStateFlow(state)
    val stateFlow: StateFlow<State> = _stateFlow

    private var backing: List<T> = emptyList()
    private var currentPage: Int = 0
    private var hasReachedEnd = false // TODO: Maybe move to the State?

    fun start() {
        if (currentPage == 0) tryFetchNextPage()
    }

    // This is the current backing list snapshot, do not hold on to this reference as it will change as pages are loaded in
    fun snapshot(): List<T> = backing.toList()

    fun getCount(): Int {
        return backing.size
    }

    // TODO: If all results are filtered or diffed out, this wont get triggered, thus not triggering fetchNextPage()
    operator fun get(index: Int): T {
        Timber.d("SHABAM get($index)")
        if (getCount() < index + pageSize) tryFetchNextPage()

        return backing[index]
    }

    private lateinit var tryFetchNextPageJob: Job
    private fun tryFetchNextPage() {
        if (::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isActive || hasReachedEnd || state != State.IDLE) return

        coroutineScope.launch {
            fetchNextPage()
        }
    }

    private suspend fun fetchNextPage() {
        Timber.d("SHABAM fetchNextPage() currentPage: $currentPage")

        val page = currentPage + 1
        state = State.FETCHING
        Timber.d("SHABAM getData($page)")

        when (val result = dataSource.fetchPage(page, pageSize)) {
            is DataSource.FetchResult.Success -> {
                val filterFunc = filterFunc
                val originalData = result.data
                val data = if (filterFunc == null) originalData else originalData.filter(filterFunc)

                backing = backing + data
                currentPage++
                hasReachedEnd = result.hasReachedEnd
                state = State.IDLE

                Timber.d("SHABAM getData success result.size: ${data.size}")
                // If the results are filtered out we should get new page right away
                if (!hasReachedEnd && data.isEmpty()) {
                    fetchNextPage()
                    return
                }
                // TODO: check if needed
                Handler(Looper.getMainLooper()).post {
                    adapterCallback?.pageFetched(data)
                }
            }
            is DataSource.FetchResult.Failure -> {
                state = State.ERROR
                Timber.d("SHABAM getData failure error: ${result.error}")
            }
        }
    }

    fun retry() {
        require(state == State.ERROR)
        state = State.IDLE
        tryFetchNextPage()
    }

    enum class State {
        IDLE,
        FETCHING,
        ERROR
    }

    interface AdapterCallback<T : Any> {
        fun pageFetched(page: List<T>)
    }

    // Seeing that DiffUtil doesnt actually prevent repeated items emitted from the DataSource from being shown:
    // TODO: Implement distinctBy {}

    //    fun <R : Any> map(transform: (T) -> R): PagedList<R> =
    //        PagedList(
    //            coroutineScope,
    //            pageSize,
    //            dataSource.map(transform),
    //        )
    //
    //    fun filter(predicate: (T) -> Boolean): PagedList<T> =
    //        PagedList(
    //            coroutineScope,
    //            pageSize,
    //            dataSource.filter(predicate)
    //        )

    fun filter(predicate: (T) -> Boolean): PagingSource<T> =
        PagingSource(
            coroutineScope,
            pageSize,
            dataSource,
        ).apply {
            filterFunc = predicate
        }
}

class AsyncPagingSourceDiffer<T : Any>(
    private val adapter: RecyclerView.Adapter<*>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val diffingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val listUpdateCallback = DebugListUpdateCallback(adapter)

    var currentList: PagingSource<T>? = null
        private set

    var currentSnapshot: List<T>? = null
        private set

    fun pageFetched(page: List<T>) {
        val oldSnapshot = currentSnapshot.orEmpty()
        currentSnapshot = oldSnapshot + page
        listUpdateCallback.onInserted(oldSnapshot.size, page.size)
    }

    private lateinit var diffingJob: Job

    // TODO: This is called when a new PagedList is submitted and when a new page is attached, maybe we should have different methods
    suspend fun submitList(newList: PagingSource<T>?) {
        if (::diffingJob.isInitialized && diffingJob.isActive) diffingJob.cancel()

        val oldSnapshot = currentSnapshot
        val newSnapshot = newList?.snapshot()

        if (oldSnapshot == null && newSnapshot == null) return
        if (oldSnapshot == null && newSnapshot != null) {
            listUpdateCallback.onInserted(0, newSnapshot.size)
            currentSnapshot = newSnapshot
            currentList = newList
            return
        }
        if (oldSnapshot != null && newSnapshot == null) {
            listUpdateCallback.onRemoved(0, oldSnapshot.size)
            currentSnapshot = null
            currentList = null
            return
        }
        require(oldSnapshot != null)
        require(newSnapshot != null)

        diffingJob = coroutineScope {
            launch(diffingDispatcher) {
                val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = oldSnapshot.size
                    override fun getNewListSize(): Int = newSnapshot.size
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        diffCallback.areItemsTheSame(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                        diffCallback.areContentsTheSame(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])

                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? =
                        diffCallback.getChangePayload(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])
                })

                mainDispatcher.dispatch(coroutineContext) {
                    currentSnapshot = newSnapshot
                    currentList = newList
                    diffResult.dispatchUpdatesTo(listUpdateCallback)
                }
            }
        }
    }
}

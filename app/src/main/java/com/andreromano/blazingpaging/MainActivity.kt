package com.andreromano.blazingpaging

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.extensions.ActionFlow
import com.andreromano.blazingpaging.extensions.value
import com.andreromano.blazingpaging.other.*
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_error.view.*
import kotlinx.android.synthetic.main.item_header.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber


/** TODO:
 *      Split library into module
 *      Implement proper samples using real world API's(Reddit et al.), with differing pagination styles(incrementing page number, key for the next page that comes on the result, etc..)
 *      Allow nullable DataSource Key, to allow the initialKey to be nullable, there are some usecases where the first page has no key, ie. null
 *      Add State.DIFFING? Because diffing may take a while
 *      Allow different viewtypes that are not part of the PagedList and are not counted towards the pagination(similar to Epoxy)
 *      Allow DB+Network
 *      AsyncDiffUtil for submitList(pagedList)
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private val dataAdapter by lazy {
        DataPagedListAdapter(lifecycleScope)
    }

    private val footerAdapter by lazy {
        FooterAdapter({ pagedListFlow.value.retry() })
    }

    private val concatAdapter by lazy {
        PagedListAdapterConcat.build(dataAdapter, footerAdapter = footerAdapter, headerAdapter = HeaderAdapter().apply { submitList(listOf("Some header")) })
    }

    private val pagedListFlow = ActionFlow<PagedList<Data>>()

    private val pagedListFlowState = pagedListFlow.flatMapLatest { it.state }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = concatAdapter

        sw_api_fail.isChecked = !Api.shouldFail
        sw_api_fail.setOnCheckedChangeListener { _, isChecked -> Api.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
            pagedListFlow.value.retry()
        }
        sw_diffing_halt.setOnCheckedChangeListener { _, isChecked ->

        }
        btn_new_pagedlist.setOnClickListener {
//            val thingamabob = Thingamabob(lifecycleScope, Thingamabob.Config(1, 10), CustomDataSource(Repository::getSequentialData))
            val thingamabob = Thingamabob(lifecycleScope, Thingamabob.Config("one", 10), StringDataSource())
            pagedListFlow.tryEmit(thingamabob.buildPagedList())
        }
        fab.setOnClickListener {

        }

        lifecycleScope.launchWhenCreated {
            pagedListFlow.collect {
                dataAdapter.submitList(it)
            }
        }

        lifecycleScope.launchWhenCreated {
            pagedListFlowState.collect {
                tv_title.text = it.javaClass.simpleName
                btn_retry.isEnabled = it is Thingamabob.State.Error

                recyclerView.post {
                    val footer = when (it) {
                        Thingamabob.State.Idle -> null
                        Thingamabob.State.Fetching -> FooterAdapter.Item.Loading
                        is Thingamabob.State.Error -> FooterAdapter.Item.Error(it.error.errorMessage)
                    }
                    footerAdapter.submitList(listOfNotNull(footer))
                }
            }
        }

        setupBug_1_solution()
    }

    private fun setupBug_1_solution() {
        pagedListFlow.value = Thingamabob(lifecycleScope,
            Thingamabob.Config(1, 10),
            CustomDataSource(Repository::getSequentialData)).buildPagedList() //.filter { it.id % 2 == 0 }.map { it.copy(name = "ALKJDALJ ${it.id}") }
    }
}


abstract class DataSource<Key : Any, Data> {
    abstract suspend fun fetchPage(key: Key, pageSize: Int): FetchResult<Key, Data>

    sealed class FetchResult<out Key, out Data> {
        data class Success<Key, out Data>(val nextPageKey: Key?, val data: List<Data>) : FetchResult<Key, Data>()
        data class Failure(val error: ErrorKt) : FetchResult<Nothing, Nothing>()
    }
}

data class PagedList<T : Any> internal constructor(
    internal val coroutineScope: CoroutineScope,
    val pages: Flow<Page<T>>,
    val state: Flow<Thingamabob.State>,
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

fun <T : Any, R : Any> PagedList<T>.map(transform: (T) -> R): PagedList<R> =
    PagedList<R>(coroutineScope, pages.map { it.map(transform) }, state, fetchNextPage, retry, pageSize, prefetchDistance)

fun <T : Any> PagedList<T>.filter(predicate: (T) -> Boolean): PagedList<T> =
    PagedList<T>(coroutineScope, pages.map { it.filter(predicate) }, state, fetchNextPage, retry, pageSize, prefetchDistance)

fun <T : Any, R : Any> Page<T>.map(transform: (T) -> R): Page<R> = Page<R>(items.map(transform))

fun <T : Any> Page<T>.filter(predicate: (T) -> Boolean): Page<T> = Page<T>(items.filter(predicate))


/**
 * @param prefetchDistance Is the minimum amount of items available in front of the current position before fetching a new page, defaults to pageSize * 2
 */
// TODO: Name it
data class Thingamabob<Key : Any, T : Any>(
    private val coroutineScope: CoroutineScope,
    private val config: Config<Key>,
    private val dataSource: DataSource<Key, T>,
) {
    data class Config<Key>(
        val initialKey: Key,
        val pageSize: Int,
        val prefetchDistance: Int = pageSize * 2,
    )

    // This is starting to shape up like the PageEvent's the new paging library uses, we need this to signal the PagedList that a new page was fetched
    // the pagedlist will in turn filter the results if it needs to and it will determine if it should get another page or not,
    // this way all of this logic goes smoothly to the pagedlist side while still separating the logic between these classes
    // ^ Old comment, we're now just using the backingList to always send the full new list and let the differ handle the rest
    val backingList = ActionFlow<Page<T>>()
    val state = MutableStateFlow<State>(State.Idle)
    private var nextPageKey: Key? = config.initialKey

    fun buildPagedList(): PagedList<T> = PagedList(
        coroutineScope,
        backingList,
        state,
        ::tryFetchNextPage,
        ::retry,
        config.pageSize,
        config.prefetchDistance,
    )

    private lateinit var tryFetchNextPageJob: Job
    private fun tryFetchNextPage() {
        if (::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isActive || nextPageKey == null || state.value != State.Idle) {
            Timber.d("SHABAM tryFetchNextPage() halted tryFetchNextPageJob.isActive: ${::tryFetchNextPageJob.isInitialized && tryFetchNextPageJob.isActive} || hasReachedEnd: ${nextPageKey == null} || state.value != State.Idle: ${state.value != State.Idle}")
            return
        }

        coroutineScope.launch {
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

    sealed class State {
        object Idle : State()
        object Fetching : State()
        data class Error(val error: ErrorKt) : State()
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

class Page<T>(val items: List<T>)

class HeaderAdapter : ListAdapter<String, HeaderAdapter.ViewHolder>(EqualityDiffUtil()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_header, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(header: String) = with(containerView) {
            tv_header.text = header
        }
    }
}

class FooterAdapter(private val retry: () -> Unit) : ListAdapter<FooterAdapter.Item, FooterAdapter.ViewHolder>(EqualityDiffUtil()) {

    sealed class Item {
        object Loading : Item()
        data class Error(val message: String) : Item()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        Item.Loading -> R.layout.item_loading
        is Item.Error -> R.layout.item_error
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        R.layout.item_loading -> ViewHolder.Loading(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
        R.layout.item_error -> ViewHolder.Error(LayoutInflater.from(parent.context).inflate(viewType, parent, false), retry)
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = when (holder) {
        is ViewHolder.Loading -> {
        }
        is ViewHolder.Error -> holder.bind((getItem(position) as Item.Error).message)
    }

    sealed class ViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        class Loading(override val containerView: View) : ViewHolder(containerView)
        class Error(override val containerView: View, private val retry: () -> Unit) : ViewHolder(containerView) {
            fun bind(message: String) = with(containerView) {
                tv_error.text = message
                ib_retry.setOnClickListener {
                    retry()
                }
            }
        }
    }
}

object PagedListAdapterConcat {

    fun <T : Any> build(
        pagedItemAdapter: PagedListAdapter<T, *>,
        headerAdapter: ListAdapter<*, *>? = null,
        footerAdapter: ListAdapter<*, *>? = null,
    ) = ConcatAdapter(listOfNotNull(headerAdapter, pagedItemAdapter, footerAdapter))
}

abstract class PagedListAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    private val coroutineScope: CoroutineScope,
    diffUtil: DiffUtil.ItemCallback<T>,
) : RecyclerView.Adapter<VH>() {
    private var pagedList: PagedList<T>? = null

    private var currentList: List<T> = emptyList()

    private lateinit var diffingJob: Job
    fun submitList(newList: PagedList<T>?) {
        if (pagedList == newList) return
        if (pagedList != null) {
            if (::diffingJob.isInitialized) diffingJob.cancel()
            notifyItemRangeRemoved(0, currentList.size)
            currentList = emptyList()
        }

        pagedList = newList

        val pagedList = pagedList ?: return

        diffingJob = pagedList.pages
            .onEach { newPage ->
                Timber.i("SHABAM Adapter diffingJob.onEach newPage: $newPage currentList: $currentList")
                val oldList = currentList
                currentList = currentList + newPage.items
                if (currentList.size - currentPosition < pagedList.prefetchDistance) pagedList.fetchNextPage()
                notifyItemRangeInserted(oldList.size, newPage.items.size)
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
        Timber.i("SHABAM Adapter getItem($position) = $item differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return item
    }

    override fun getItemCount(): Int {
        Timber.i("SHABAM Adapter getItemCount() = ${currentList.size} differ.snapshot: $currentList ${System.identityHashCode(currentList)}")
        return currentList.size
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

class EqualityDiffUtil<T : Any> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
}
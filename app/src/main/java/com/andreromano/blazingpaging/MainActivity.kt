package com.andreromano.blazingpaging

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber


/** TODO:
 *      DiffUtil
 *      AsyncDiffUtil
 *      Headers/Footers
 *      Allow different viewtypes that are not part of the PagedList and are not counted towards the pagination(similar to Epoxy)
 *      Allow DB+Network
 *
 */
class MainActivity : AppCompatActivity() {

    private val dataAdapter by lazy {
        DataPagedListAdapter(lifecycleScope)
    }

    private val pagedListFlow = MutableStateFlow(PagedList(lifecycleScope, 10, DiffUtilDataSource()))

    private val pagedListFlowState = pagedListFlow.flatMapLatest { it.stateFlow }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = dataAdapter

        sw_switch.isChecked = !Api.shouldFail
        sw_switch.setOnCheckedChangeListener { _, isChecked -> Api.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
            pagedListFlow.value.retry()
        }
        fab.setOnClickListener {
            pagedListFlow.value = PagedList(lifecycleScope, 10, SomeDataSource())//.map { it.copy(name = "${it.name} aljsdaj") }.filter { it.id > 20 }
        }

        lifecycleScope.launchWhenCreated {
            pagedListFlow.collect {
                dataAdapter.submitList(it)
            }
        }

        lifecycleScope.launchWhenCreated {
            pagedListFlowState.collect {
                tv_title.text = it.name
                btn_retry.visibility = if (it == PagedList.State.ERROR) View.VISIBLE else View.GONE
            }
        }
    }

}


abstract class DataSource<T> {
    abstract suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T>

    sealed class FetchResult<out T> {
        data class Success<out T>(val data: List<T>, val hasReachedEnd: Boolean) : FetchResult<T>()
        data class Failure(val error: ErrorKt) : FetchResult<Nothing>()
    }


    fun <R> map(transform: (T) -> R): DataSource<R> = object : DataSource<R>() {
        override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<R> = this@DataSource.fetchPage(page, pageSize).map(transform)
    }

    fun filter(predicate: (T) -> Boolean): DataSource<T> = object : DataSource<T>() {
        override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T> = this@DataSource.fetchPage(page, pageSize).filter(predicate)
    }

    private fun <T, R> FetchResult<T>.map(transform: (T) -> R): FetchResult<R> = when (this) {
        is FetchResult.Success -> FetchResult.Success(this.data.map(transform), this.hasReachedEnd)
        is FetchResult.Failure -> this
    }
    private fun <T> FetchResult<T>.filter(predicate: (T) -> Boolean): FetchResult<T> = when (this) {
        is FetchResult.Success -> FetchResult.Success(this.data.filter(predicate), this.hasReachedEnd)
        is FetchResult.Failure -> this
    }
}

class SomeDataSource : DataSource<Data>() {
    override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<Data> =
        when (val result = Repository.getData(page, pageSize)) {
            is ResultKt.Success -> FetchResult.Success(result.data, result.data.size < pageSize)
            is ResultKt.Failure -> FetchResult.Failure(result.error)
        }
}

class DiffUtilDataSource : DataSource<Data>() {
    override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<Data> =
        when (val result = Repository.getDiffUtilData(page, pageSize)) {
            is ResultKt.Success -> FetchResult.Success(result.data, result.data.size < pageSize)
            is ResultKt.Failure -> FetchResult.Failure(result.error)
        }
}

class PagedList<T : Any>(
    private val coroutineScope: CoroutineScope,
    private val pageSize: Int,
    private val dataSource: DataSource<T>,
) {
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
        if (currentPage == 0) fetchNextPage()
    }

    // This is the current backing list snapshot, do not hold on to this reference as it will change as pages are loaded in
    fun snapshot(): List<T> = backing.toList()

    fun getCount(): Int {
        return backing.size
    }

    // TODO: If all results are filtered or diffed out, this wont get triggered, thus not triggering fetchNextPage()
    operator fun get(index: Int): T {
        Timber.d("SHABAM get($index)")
        if (getCount() < index + pageSize) fetchNextPage()

        return backing[index]
    }

    private lateinit var fetchPageJob: Job
    private fun fetchNextPage() {
        Timber.d("SHABAM fetchNextPage() currentPage: $currentPage")

        if (::fetchPageJob.isInitialized && fetchPageJob.isActive || hasReachedEnd || state != State.IDLE) return

        val page = currentPage + 1
        fetchPageJob = coroutineScope.launch {
            state = State.FETCHING
            Timber.d("SHABAM getData($page)")
            val result = dataSource.fetchPage(page, pageSize)

            when (result) {
                is DataSource.FetchResult.Success -> {
                    val data = result.data
                    backing = backing + data
                    currentPage++
                    // TODO: check if needed
                    Timber.d("SHABAM getData success result.size: ${data.size}")
                    Handler(Looper.getMainLooper()).post {
                        hasReachedEnd = result.hasReachedEnd
                        state = State.IDLE
                        adapterCallback?.pageFetched(data)
                    }
                }
                is DataSource.FetchResult.Failure -> {
                    state = State.ERROR
                    Timber.d("SHABAM getData failure error: ${result.error}")
                }
            }
        }
    }

    fun retry() {
        require(state == State.ERROR)
        state = State.IDLE
        fetchNextPage()
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

    fun <R : Any> map(transform: (T) -> R): PagedList<R> =
        PagedList(
            coroutineScope,
            pageSize,
            dataSource.map(transform),
        )

    fun filter(predicate: (T) -> Boolean): PagedList<T> =
        PagedList(
            coroutineScope,
            pageSize,
            dataSource.filter(predicate)
        )
}

class AsyncPagedListDiffer<T : Any>(
    private val adapter: RecyclerView.Adapter<*>,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val diffingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val listUpdateCallback = DebugListUpdateCallback(adapter)

    var currentList: PagedList<T>? = null
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
    suspend fun submitList(newList: PagedList<T>?) {
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

                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = diffCallback.areItemsTheSame(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = diffCallback.areContentsTheSame(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])

                    override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? = diffCallback.getChangePayload(oldSnapshot[oldItemPosition], newSnapshot[newItemPosition])
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

abstract class PagedListAdapter<T : Any, VH : RecyclerView.ViewHolder>(
    private val coroutineScope: CoroutineScope,
    diffUtil: DiffUtil.ItemCallback<T>,
) : RecyclerView.Adapter<VH>() {
    private val differ = AsyncPagedListDiffer<T>(this, diffUtil)
    private var list: PagedList<T>? = null

    private val pagedListCallback = object : PagedList.AdapterCallback<T> {
        override fun pageFetched(page: List<T>) {
            coroutineScope.launch {
//                differ.pageFetched(page)
                differ.submitList(list)
            }
        }
    }

    suspend fun submitList(newList: PagedList<T>?) {
        list?.adapterCallback = null
        newList?.adapterCallback = pagedListCallback
        list = newList
        differ.submitList(newList)
        list?.start()
    }

    protected fun getItem(position: Int): T = differ.currentList?.get(position)!!

    override fun getItemCount(): Int = differ.currentList?.getCount() ?: 0
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

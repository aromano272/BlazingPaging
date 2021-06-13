package com.andreromano.blazingpaging

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val dataAdapter by lazy {
        DataPagedListAdapter()
    }

    private val stringAdapter by lazy {
        StringPagedListAdapter()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = dataAdapter

        val pagedList = PagedList(lifecycleScope, 10, SomeDataSource()).filter { it.id % 2 == 0 }

        sw_switch.isChecked = !Api.shouldFail
        sw_switch.setOnCheckedChangeListener { _, isChecked -> Api.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
            pagedList.retry()
        }

        lifecycleScope.launchWhenCreated {
            pagedList.stateFlow.collect {
                tv_title.text = it.name
                btn_retry.visibility = if (it == PagedList.State.ERROR) View.VISIBLE else View.GONE
            }
        }



        dataAdapter.submitList(pagedList)
    }

    abstract class DataSource<T> {
        abstract suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T>

        fun <R> map(transform: (T) -> R): DataSource<R> = object : DataSource<R>() {
            override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<R> = this@DataSource.fetchPage(page, pageSize).map(transform)
        }

        fun filter(predicate: (T) -> Boolean): DataSource<T> = object : DataSource<T>() {
            override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T> = this@DataSource.fetchPage(page, pageSize).filter(predicate)
        }

        sealed class FetchResult<out T> {
            data class Success<out T>(val data: List<T>, val hasReachedEnd: Boolean) : FetchResult<T>()
            data class Failure(val error: ErrorKt) : FetchResult<Nothing>()
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

    class PagedList<T>(
        private val coroutineScope: CoroutineScope,
        private val pageSize: Int,
        private val dataSource: DataSource<T>,
    ) {
        internal lateinit var adapterCallback: AdapterCallback
        var state: State = State.IDLE
            private set(value) {
                field = value
                _stateFlow.value = value
            }
        private val _stateFlow = MutableStateFlow(state)
        val stateFlow = _stateFlow

        private val cache: MutableList<T> = mutableListOf()
        private var currentPage: Int = 0
        private var hasReachedEnd = false // TODO: Maybe move to the State?

        fun <R> map(transform: (T) -> R): PagedList<R> =
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

        fun getCount(): Int {
            if (currentPage == 0) fetchNextPage()
            return cache.size
        }

        fun get(index: Int): T {
            Timber.d("SHABAM get($index)")
            if (getCount() < index + pageSize) fetchNextPage()

            return cache[index]
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
                        cache.addAll(data)
                        currentPage++
                        // TODO: check if needed
                        Timber.d("SHABAM getData success result.size: ${data.size}")
                        Handler(Looper.getMainLooper()).post {
                            hasReachedEnd = result.hasReachedEnd
                            state = State.IDLE
                            adapterCallback.notifyItemRangeInserted(cache.size, data.size)
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

        interface AdapterCallback {
            fun notifyItemRangeInserted(positionStart: Int, itemCount: Int)
        }
    }

    abstract class PagedListAdapter<T, VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
        private lateinit var list: PagedList<T>

        private val pagedListCallback = object : PagedList.AdapterCallback {
            override fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
                this@PagedListAdapter.notifyItemRangeInserted(positionStart, itemCount)
            }
        }

        fun submitList(list: PagedList<T>) {
            list.adapterCallback = pagedListCallback
            this.list = list
        }

        protected fun getItem(position: Int): T = list.get(position)

        override fun getItemCount(): Int = list.getCount()
    }

    class DataPagedListAdapter : PagedListAdapter<Data, DataViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder =
            DataViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false))

        override fun onBindViewHolder(holder: DataViewHolder, position: Int) = holder.bind(getItem(position))

    }

    class StringPagedListAdapter : PagedListAdapter<String, StringViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder =
            StringViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false))

        override fun onBindViewHolder(holder: StringViewHolder, position: Int) = holder.bind(getItem(position))

    }
}
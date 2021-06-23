package com.andreromano.blazingpaging.sample

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.*
import com.andreromano.blazingpaging.PagedList
import com.andreromano.blazingpaging.PagedListAdapter
import com.andreromano.blazingpaging.Thingamabob
import com.andreromano.blazingpaging.sample.core.ErrorKt
import com.andreromano.blazingpaging.sample.extensions.ActionFlow
import com.andreromano.blazingpaging.sample.extensions.value
import com.andreromano.blazingpaging.sample.other.*
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_error.view.*
import kotlinx.android.synthetic.main.item_header.*
import kotlinx.coroutines.flow.*


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

    private val pagedListFlow = ActionFlow<PagedList<Data, ErrorKt>>()

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

class EqualityDiffUtil<T : Any> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: T, newItem: T): Boolean = oldItem == newItem
}
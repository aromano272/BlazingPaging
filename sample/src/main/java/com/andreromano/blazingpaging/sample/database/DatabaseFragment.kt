package com.andreromano.blazingpaging.sample.database

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.andreromano.blazingpaging.DataSource
import com.andreromano.blazingpaging.PagedList
import com.andreromano.blazingpaging.Thingamabob
import com.andreromano.blazingpaging.extensions.combine
import com.andreromano.blazingpaging.extensions.map
import com.andreromano.blazingpaging.sample.R
import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import com.andreromano.blazingpaging.sample.common.core.Resource
import com.andreromano.blazingpaging.sample.common.extensions.ActionFlow
import com.andreromano.blazingpaging.sample.common.extensions.errorMessage
import com.andreromano.blazingpaging.sample.common.extensions.value
import com.andreromano.blazingpaging.sample.common.extensions.viewLifecycleScope
import com.andreromano.blazingpaging.sample.common.ui.FooterAdapter
import com.andreromano.blazingpaging.sample.common.ui.HeaderAdapter
import com.andreromano.blazingpaging.sample.common.ui.PagedListAdapterConcat
import com.andreromano.blazingpaging.sample.other.OtherApi
import kotlinx.android.synthetic.main.fragment_reddit.*
import kotlinx.android.synthetic.main.view_diagnostics.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.koin.android.ext.android.inject
import timber.log.Timber

class DatabaseFragment : Fragment(R.layout.fragment_database) {

    private val repository: DatabaseRepository by inject()

    private val dataAdapter by lazy {
        DatabaseDataAdapter(viewLifecycleScope) {
            changeCheckStateAction.value = it
        }
    }

    private val footerAdapter by lazy {
        FooterAdapter({ pagedListFlow.value.retry() })
    }

    private val concatAdapter by lazy {
        PagedListAdapterConcat.build(dataAdapter, footerAdapter = footerAdapter, headerAdapter = HeaderAdapter().apply { submitList(listOf("Some header")) })
    }

    private val pagedListFlow = ActionFlow<PagedList<DatabaseDataAdapter.Item, ErrorKt>>()

    private val pagedListFlowState = pagedListFlow.flatMapLatest { it.state }

    private val changeCheckStateAction = ActionFlow<DataItem>()
    private val changeCheckStateResult = changeCheckStateAction.flatMapMerge { data ->
        flow {
            emit(data.id to Resource.Loading)
            delay(2000)
            val result =
                if (data.isChecked) repository.uncheck(data.id)
                else repository.check(data.id)
            emit(data.id to Resource.Success(result))
        }
    }

    private val changeCheckStateLoadingIds =
        changeCheckStateResult.scan(emptyMap<Int, Resource<Unit>>()) { acc, curr ->
            acc + curr
        }.mapLatest {
            it.mapNotNull { (id, result) -> if (result is Resource.Loading) id else null }
        }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = concatAdapter

        sw_api_fail.isChecked = !OtherApi.shouldFail
        sw_api_fail.setOnCheckedChangeListener { _, isChecked -> OtherApi.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
            pagedListFlow.value.retry()
        }
        val someFlow = ActionFlow<Boolean>(false)
        sw_diffing_halt.setOnCheckedChangeListener { _, isChecked ->
            someFlow.value = isChecked
        }
        btn_new_pagedlist.setOnClickListener {
            pagedListFlow.value = repository.getDataPagedList(viewLifecycleScope).map { DatabaseDataAdapter.Item(it, false) }
        }

        pagedListFlow.asLiveData().observe(viewLifecycleOwner) {
            dataAdapter.submitList(it)
        }

        pagedListFlowState.asLiveData().observe(viewLifecycleOwner) {
            tv_status.text = it.javaClass.simpleName.uppercase()
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

        pagedListFlow.value = repository.getDataPagedList(viewLifecycleScope).combine(changeCheckStateLoadingIds) { data, loadingIds ->
            DatabaseDataAdapter.Item(data, loadingIds.contains(data.id))
        }
    }
}
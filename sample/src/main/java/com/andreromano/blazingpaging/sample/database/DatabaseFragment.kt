package com.andreromano.blazingpaging.sample.database

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import com.andreromano.blazingpaging.DataSource
import com.andreromano.blazingpaging.PagedList
import com.andreromano.blazingpaging.Thingamabob
import com.andreromano.blazingpaging.sample.R
import com.andreromano.blazingpaging.sample.common.core.ErrorKt
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
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.android.ext.android.inject

class DatabaseFragment : Fragment(R.layout.fragment_database) {

    private val repository: DatabaseRepository by inject()

    private val dataAdapter by lazy {
        DatabaseDataAdapter(viewLifecycleScope) {
            // @Cleanup
            viewLifecycleScope.launchWhenCreated {
                if (it.isChecked) repository.uncheck(it.id)
                else repository.check(it.id)
            }
        }
    }

    private val footerAdapter by lazy {
        FooterAdapter({ pagedListFlow.value.retry() })
    }

    private val concatAdapter by lazy {
        PagedListAdapterConcat.build(dataAdapter, footerAdapter = footerAdapter, headerAdapter = HeaderAdapter().apply { submitList(listOf("Some header")) })
    }

    private val pagedListFlow = ActionFlow<PagedList<DataItem, ErrorKt>>()

    private val pagedListFlowState = pagedListFlow.flatMapLatest { it.state }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = concatAdapter

        sw_api_fail.isChecked = !OtherApi.shouldFail
        sw_api_fail.setOnCheckedChangeListener { _, isChecked -> OtherApi.shouldFail = !isChecked }
        btn_retry.setOnClickListener {
            pagedListFlow.value.retry()
        }
        sw_diffing_halt.setOnCheckedChangeListener { _, isChecked ->

        }
        btn_new_pagedlist.setOnClickListener {
            pagedListFlow.value = repository.getDataPagedList(viewLifecycleScope)
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

        pagedListFlow.value = repository.getDataPagedList(viewLifecycleScope)
    }

}
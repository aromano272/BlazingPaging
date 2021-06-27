package com.andreromano.blazingpaging.sample.other

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.*
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
import com.andreromano.blazingpaging.sample.other.misc.CustomDataSource
import com.andreromano.blazingpaging.sample.other.misc.Data
import com.andreromano.blazingpaging.sample.other.misc.DataPagedListAdapter
import com.andreromano.blazingpaging.sample.other.misc.StringDataSource
import kotlinx.android.synthetic.main.fragment_other.*
import kotlinx.android.synthetic.main.view_diagnostics.*
import kotlinx.coroutines.flow.flatMapLatest


class OtherFragment : Fragment(R.layout.fragment_other) {

    private val dataAdapter by lazy {
        DataPagedListAdapter(viewLifecycleScope)
    }

    private val footerAdapter by lazy {
        FooterAdapter({ pagedListFlow.value.retry() })
    }

    private val concatAdapter by lazy {
        PagedListAdapterConcat.build(dataAdapter, footerAdapter = footerAdapter, headerAdapter = HeaderAdapter().apply { submitList(listOf("Some header")) })
    }

    private val pagedListFlow = ActionFlow<PagedList<Data, ErrorKt>>()

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
            //            val thingamabob = Thingamabob(viewLifecycleScope, Thingamabob.Config(1, 10), CustomDataSource(Repository::getSequentialData))
            val thingamabob = Thingamabob(viewLifecycleScope, Thingamabob.Config("one", 10), StringDataSource())
            pagedListFlow.value = thingamabob.buildPagedList()
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

        setupBug_1_solution()
    }

    private fun setupBug_1_solution() {
        pagedListFlow.value = Thingamabob(viewLifecycleScope,
            Thingamabob.Config(1, 10),
            CustomDataSource(Repository::getSequentialData)).buildPagedList() //.filter { it.id % 2 == 0 }.map { it.copy(name = "ALKJDALJ ${it.id}") }
    }
}


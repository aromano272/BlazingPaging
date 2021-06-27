package com.andreromano.blazingpaging.sample.reddit

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
import com.andreromano.blazingpaging.sample.common.core.ResultKt
import com.andreromano.blazingpaging.sample.common.extensions.ActionFlow
import com.andreromano.blazingpaging.sample.common.extensions.errorMessage
import com.andreromano.blazingpaging.sample.common.extensions.value
import com.andreromano.blazingpaging.sample.common.extensions.viewLifecycleScope
import com.andreromano.blazingpaging.sample.common.ui.FooterAdapter
import com.andreromano.blazingpaging.sample.common.ui.HeaderAdapter
import com.andreromano.blazingpaging.sample.other.OtherApi
import com.andreromano.blazingpaging.sample.common.ui.PagedListAdapterConcat
import com.andreromano.blazingpaging.sample.reddit.model.RedditPostResult
import com.andreromano.blazingpaging.sample.reddit.model.Sort
import kotlinx.android.synthetic.main.fragment_reddit.recyclerView
import kotlinx.android.synthetic.main.view_diagnostics.*
import kotlinx.coroutines.flow.flatMapLatest
import org.koin.android.ext.android.inject

class RedditFragment : Fragment(R.layout.fragment_reddit) {

    private val repository: RedditRepository by inject()

    private val dataAdapter by lazy {
        RedditPostsAdapter(viewLifecycleScope, 10)
    }

    private val footerAdapter by lazy {
        FooterAdapter({ pagedListFlow.value.retry() })
    }

    private val concatAdapter by lazy {
        PagedListAdapterConcat.build(dataAdapter, footerAdapter = footerAdapter, headerAdapter = HeaderAdapter().apply { submitList(listOf("Some header")) })
    }

    private val pagedListFlow = ActionFlow<PagedList<RedditPostResult, ErrorKt>>()

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
            val thingamabob = Thingamabob<RedditKey, RedditPostResult, ErrorKt>(
                viewLifecycleScope,
                Thingamabob.Config(
                    RedditKey(null, 0),
                    20,
                ),
                RedditDataSource(repository, "portugal", Sort.HOT)
            )
            // NOTE: Debug info
            dataAdapter.pageSize = 20
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

        pagedListFlow.value = Thingamabob<RedditKey, RedditPostResult, ErrorKt>(
            viewLifecycleScope,
            Thingamabob.Config(
                RedditKey(null, 0),
                10,
            ),
            RedditDataSource(repository, "androiddev", Sort.HOT)
        ).buildPagedList()

    }

    data class RedditKey(
        val after: String?,
        val count: Int,
    )

    class RedditDataSource(
        private val repository: RedditRepository,
        private val subreddit: String,
        private val sort: Sort,
    ) : DataSource<RedditKey, RedditPostResult, ErrorKt>() {
        override suspend fun fetchPage(key: RedditKey, pageSize: Int): FetchResult<RedditKey, RedditPostResult, ErrorKt> {
            val result = repository.getListing(
                subreddit = subreddit,
                sort = sort,
                before = null,
                after = key.after,
                limit = pageSize,
                count = key.count,
            )

            return when (result) {
                is ResultKt.Success -> {
                    val posts = result.data.data.children.map { it.data }
                    val nextPageKey = result.data.data.after?.let { RedditKey(it, key.count + posts.size) }
                    FetchResult.Success(nextPageKey, posts)
                }
                is ResultKt.Failure -> FetchResult.Failure(result.error)
            }
        }
    }

}
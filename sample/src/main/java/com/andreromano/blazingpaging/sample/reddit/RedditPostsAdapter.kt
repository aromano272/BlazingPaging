package com.andreromano.blazingpaging.sample.reddit

import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.EqualityDiffUtil
import com.andreromano.blazingpaging.PagedListAdapter
import com.andreromano.blazingpaging.sample.R
import com.andreromano.blazingpaging.sample.reddit.model.RedditPostResult
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_reddit_post.view.*
import kotlinx.coroutines.CoroutineScope

class RedditPostsAdapter(
    coroutineScope: CoroutineScope,
    var pageSize: Int,
) : PagedListAdapter<RedditPostResult, RedditPostsAdapter.ViewHolder>(coroutineScope, EqualityDiffUtil()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_reddit_post, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(
            getItem(position),
            position % 2 == 0,
            if (position % pageSize == 0) position / pageSize else null
        )

    class ViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(post: RedditPostResult, hasGrayBackground: Boolean, pageHeader: Int?) = with(containerView) {
            setBackgroundColor(if (hasGrayBackground) Color.parseColor("#10000000") else Color.WHITE)

            tv_score.text = post.score.toString()
            tv_title.text = post.title
            tv_author.text = post.author
            val abbrDate = DateUtils.getRelativeTimeSpanString(
                post.created_utc.toLong() * 1000,
                System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            tv_timestamp.text = abbrDate

            tv_page_header.visibility = if (pageHeader != null) View.VISIBLE else View.GONE
            tv_page_header.text = "Page $pageHeader"
        }
    }
}
package com.andreromano.blazingpaging.sample.database

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.PagedListAdapter
import com.andreromano.blazingpaging.sample.R
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_data.view.*
import kotlinx.coroutines.CoroutineScope

class DatabaseDataAdapter(
    coroutineScope: CoroutineScope,
    private val checkClicked: (DataItem) -> Unit,
) : PagedListAdapter<DataItem, DatabaseDataAdapter.ViewHolder>(coroutineScope, DIFF_UTIL) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false), checkClicked)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        override val containerView: View,
        private val checkClicked: (DataItem) -> Unit,
    ) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(item: DataItem) = with(containerView) {
            tv_data.text = "${item.id} ${item.title}"
            btn_checked.visibility = View.VISIBLE
            btn_checked.isSelected = item.isChecked
            btn_checked.setOnClickListener { checkClicked(item) }
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<DataItem>() {
            override fun areItemsTheSame(oldItem: DataItem, newItem: DataItem): Boolean = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DataItem, newItem: DataItem): Boolean = oldItem == newItem
        }
    }

}
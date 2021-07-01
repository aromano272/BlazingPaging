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
) : PagedListAdapter<DatabaseDataAdapter.Item, DatabaseDataAdapter.ViewHolder>(coroutineScope, DIFF_UTIL) {

    data class Item(val data: DataItem, val isLoading: Boolean)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false), checkClicked)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    class ViewHolder(
        override val containerView: View,
        private val checkClicked: (DataItem) -> Unit,
    ) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        fun bind(item: Item) = with(containerView) {
            val (item, isLoading) = item
            tv_data.text = "${item.id} ${item.title}"
            btn_checked.visibility = View.VISIBLE
            btn_checked.isSelected = item.isChecked
            if (isLoading) btn_checked.setOnClickListener(null)
            else btn_checked.setOnClickListener { checkClicked(item) }
            btn_checked.alpha = if (isLoading) 0.5f else 1.0f
        }
    }

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem.data.id == newItem.data.id

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
        }
    }

}
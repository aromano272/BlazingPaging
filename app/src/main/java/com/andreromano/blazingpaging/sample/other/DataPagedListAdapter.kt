package com.andreromano.blazingpaging.sample.other

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import com.andreromano.blazingpaging.PagedListAdapter
import com.andreromano.blazingpaging.sample.R
import kotlinx.coroutines.CoroutineScope

class DataPagedListAdapter(coroutineScope: CoroutineScope) : PagedListAdapter<Data, DataViewHolder>(coroutineScope, object : DiffUtil.ItemCallback<Data>() {
    override fun areItemsTheSame(oldItem: Data, newItem: Data): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Data, newItem: Data): Boolean = oldItem == newItem
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder =
        DataViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_data, parent, false))
    override fun onBindViewHolder(holder: DataViewHolder, position: Int) = holder.bind(getItem(position))
}
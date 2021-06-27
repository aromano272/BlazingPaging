package com.andreromano.blazingpaging.sample.common.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.sample.R
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_header.*

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
package com.andreromano.blazingpaging.other

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.other.Data
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_data.*

class DataViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
    fun bind(item: Data) = with(containerView) {
        tv_data.text = item.name
    }
}
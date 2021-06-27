package com.andreromano.blazingpaging.sample.common.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.andreromano.blazingpaging.sample.R
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.item_error.*

class FooterAdapter(private val retry: () -> Unit) : ListAdapter<FooterAdapter.Item, FooterAdapter.ViewHolder>(EqualityDiffUtil()) {

    sealed class Item {
        object Loading : Item()
        data class Error(val message: String) : Item()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        Item.Loading -> R.layout.item_loading
        is Item.Error -> R.layout.item_error
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = when (viewType) {
        R.layout.item_loading -> ViewHolder.Loading(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
        R.layout.item_error -> ViewHolder.Error(LayoutInflater.from(parent.context).inflate(viewType, parent, false), retry)
        else -> throw IllegalStateException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = when (holder) {
        is ViewHolder.Loading -> {
        }
        is ViewHolder.Error -> holder.bind((getItem(position) as Item.Error).message)
    }

    sealed class ViewHolder(override val containerView: View) : RecyclerView.ViewHolder(containerView), LayoutContainer {
        class Loading(override val containerView: View) : ViewHolder(containerView)
        class Error(override val containerView: View, private val retry: () -> Unit) : ViewHolder(containerView) {
            fun bind(message: String) = with(containerView) {
                tv_error.text = message
                ib_retry.setOnClickListener {
                    retry()
                }
            }
        }
    }
}
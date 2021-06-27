package com.andreromano.blazingpaging.sample.common.ui

import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ListAdapter
import com.andreromano.blazingpaging.PagedListAdapter

object PagedListAdapterConcat {
    fun <T : Any> build(
        pagedItemAdapter: PagedListAdapter<T, *>,
        headerAdapter: ListAdapter<*, *>? = null,
        footerAdapter: ListAdapter<*, *>? = null,
    ) = ConcatAdapter(listOfNotNull(headerAdapter, pagedItemAdapter, footerAdapter))
}
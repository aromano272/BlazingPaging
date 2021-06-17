package com.andreromano.blazingpaging.other

import com.andreromano.blazingpaging.DataSource
import com.andreromano.blazingpaging.core.ResultKt

class CustomDataSource<T : Any>(private val fetch: suspend (page: Int, pageSize: Int) -> ResultKt<List<T>>) : DataSource<T>() {
    override suspend fun fetchPage(page: Int, pageSize: Int): FetchResult<T> =
        when (val result = fetch(page, pageSize)) {
            is ResultKt.Success -> FetchResult.Success(result.data, result.data.size < pageSize)
            is ResultKt.Failure -> FetchResult.Failure(result.error)
        }
}
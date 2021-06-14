package com.andreromano.blazingpaging

import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.coroutines.delay

object Api {

    var shouldFail: Boolean = false

    private object ApiData {
        val NORMAL = (0 until 45).map(::transform)
        val DIFF_UTIL = (
                (0 until 7) +
                listOf(1, 2) +
                (9 until 20) +
                listOf(5, 7, 20, 20, 20) +
                (15 until 30) +
                listOf(20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20) +
                (30 until 50) +
                (5 until 10) +
                (50 until 60)
            ).map(::transform)

        fun transform(it: Int): Data = Data(it, "data $it")
    }



    suspend fun getData(
        page: Int,
        pageSize: Int,
    ): ResultKt<List<Data>> = paged(page, pageSize, ApiData.NORMAL)

    suspend fun getDiffUtilData(
        page: Int,
        pageSize: Int,
    ): ResultKt<List<Data>> = paged(page, pageSize, ApiData.DIFF_UTIL)



    private suspend fun <T> paged(page: Int, pageSize: Int, dataSet: List<T>): ResultKt<List<T>> = middleware {
        dataSet.subList(
            (page - 1) * pageSize,
            (page * pageSize).coerceAtMost(dataSet.size)
        )
    }

    private suspend fun <T> middleware(shouldFail: Boolean = this.shouldFail, call: suspend () -> T): ResultKt<T> = when (shouldFail) {
        true -> ResultKt.Success(call())
        false -> ResultKt.Failure(ErrorKt.Network)
    }
}
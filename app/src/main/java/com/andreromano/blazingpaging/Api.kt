package com.andreromano.blazingpaging

import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.core.Millis
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.coroutines.delay

object Api {

    var shouldFail: Boolean = false

    private object ApiData {
        val NORMAL = (0 until 95).map(::transform)
        val DIFF_UTIL = (
                (0 until 10) +
                // new item was inserted at top of list, with id -1, so now page 0 would return (-1 until 9) and page 2 (9 until 19),
                // but we already have (0 until 10), getting page 2 (9 until 19) would leave us with two 9s which should be diffed ou
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



    private suspend fun <T> paged(page: Int, pageSize: Int, dataSet: List<T>, delay: Millis = 2000): ResultKt<List<T>> = middleware(delay = delay) {
        dataSet.subList(
            (page - 1) * pageSize,
            (page * pageSize).coerceAtMost(dataSet.size)
        )
    }

    private suspend fun <T> middleware(shouldFail: Boolean = this.shouldFail, delay: Millis = 2000, call: suspend () -> T): ResultKt<T> {
        delay(delay)
        return when (shouldFail) {
            false -> ResultKt.Success(call())
            true -> ResultKt.Failure(ErrorKt.Network)
        }
    }
}
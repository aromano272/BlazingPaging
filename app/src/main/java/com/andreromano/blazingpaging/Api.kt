package com.andreromano.blazingpaging

import com.andreromano.blazingpaging.core.ErrorKt
import com.andreromano.blazingpaging.core.ResultKt
import kotlinx.coroutines.delay

object Api {

    var shouldFail: Boolean = false

    private val data = (0 until 45).map { Data(it, "data $it") }

    suspend fun getData(
        page: Int,
        pageSize: Int,
    ): ResultKt<List<Data>> {
        delay(2000)
        return when (shouldFail) {
            false -> ResultKt.Success(data.subList(
                (page - 1) * pageSize,
                (page * pageSize).coerceAtMost(data.size)
            ))
            true -> ResultKt.Failure(ErrorKt.Network)
        }
    }
}
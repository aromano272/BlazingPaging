package com.andreromano.blazingpaging.sample.common.network.mapper

import com.andreromano.blazingpaging.sample.common.core.ErrorKt
import com.andreromano.blazingpaging.sample.common.core.ResultKt
import com.andreromano.blazingpaging.sample.reddit.model.BaseResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

class FromBaseResponseToResultKtAdapter<T>(private val adapter: JsonAdapter<BaseResponse<T>>) : JsonAdapter<ResultKt<T>>() {
    override fun fromJson(reader: JsonReader): ResultKt<T> {
        val response = adapter.fromJson(reader) ?: return ResultKt.Failure(ErrorKt.Network.ParsingError)
        return when (response.status.ok) {
            true -> ResultKt.Success(response.data)
            false -> ResultKt.Failure(response.status.errorCode.asApiError())
        }
    }
    override fun toJson(writer: JsonWriter, value: ResultKt<T>?) {
        throw UnsupportedOperationException()
    }
}
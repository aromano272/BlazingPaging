package com.andreromano.blazingpaging.sample.network.mapper

import com.andreromano.blazingpaging.sample.core.ErrorKt
import com.andreromano.blazingpaging.sample.core.ResultKt
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

// FIXME: Probably not required
class FromBaseResponseToResultKtAdapter<T>(private val adapter: JsonAdapter<T>) : JsonAdapter<ResultKt<T>>() {
    override fun fromJson(reader: JsonReader): ResultKt<T> =
        when (val response =  adapter.fromJson(reader)) {
            null -> ResultKt.Failure(ErrorKt.Generic)
            else -> ResultKt.Success(response)
        }

    override fun toJson(writer: JsonWriter, value: ResultKt<T>?) {
        throw UnsupportedOperationException()
    }
}
package com.andreromano.blazingpaging.sample.common.network.mapper

import com.andreromano.blazingpaging.sample.common.core.ResultKt
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class ResultKtCallAdapterFactory : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        val apiResultType =
            getParameterUpperBound(0, returnType as ParameterizedType)
        if (apiResultType !is ParameterizedType || apiResultType.rawType != ResultKt::class.java) {
            return null
        }

        return ResultKtCallAdapter<Any>(
            retrofit,
            apiResultType
        )
    }
}
package com.andreromano.blazingpaging.sample.network.mapper


import com.andreromano.blazingpaging.sample.core.ResultKt
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class FromDataToResultKtAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val resultType = Types.getRawType(type)
        if (resultType == ResultKt::class.java && type is ParameterizedType) {
            val dataType = type.actualTypeArguments.first()

            val adapter: JsonAdapter<Any> = moshi.adapter(dataType)

            return FromDataToResultKtAdapter(adapter)
        }
        return null
    }
}
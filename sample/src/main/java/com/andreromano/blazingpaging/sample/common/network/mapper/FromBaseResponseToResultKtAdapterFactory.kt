package com.andreromano.blazingpaging.sample.common.network.mapper


import com.andreromano.blazingpaging.sample.common.core.ResultKt
import com.andreromano.blazingpaging.sample.reddit.model.BaseResponse
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class FromBaseResponseToResultKtAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        val resultType = Types.getRawType(type)
        if (resultType == ResultKt::class.java && type is ParameterizedType) {
            val dataType = type.actualTypeArguments.first()

            val baseResponseOfDataType = Types.newParameterizedType(BaseResponse::class.java, dataType)

            val adapter: JsonAdapter<BaseResponse<Any>> = moshi.adapter(baseResponseOfDataType)

            return FromBaseResponseToResultKtAdapter(adapter)
        }
        return null
    }
}
package com.andreromano.blazingpaging.sample.network.mapper

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.ToJson


@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
annotation class Sample

class SampleAdapter {
    @FromJson
    @Sample
    fun fromJson(string: String?): String? = string

    @ToJson
    fun toJson(@Sample value: String): Any = throw UnsupportedOperationException()
}

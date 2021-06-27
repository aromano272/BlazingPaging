package com.andreromano.blazingpaging.sample.reddit.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseResponse<T>(
    val data: T,
    val status: Status,
)

@JsonClass(generateAdapter = true)
data class Status(
    val something: String,
    val ok: Boolean,
    val errorCode: String?,
)
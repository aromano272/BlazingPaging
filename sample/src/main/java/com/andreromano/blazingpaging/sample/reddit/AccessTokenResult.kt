package com.andreromano.blazingpaging.sample.reddit

import com.andreromano.blazingpaging.sample.core.Seconds
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AccessTokenResult(
    val access_token: String,
    val token_type: String,
    val expires_in: Seconds,
    val scope: String,
)
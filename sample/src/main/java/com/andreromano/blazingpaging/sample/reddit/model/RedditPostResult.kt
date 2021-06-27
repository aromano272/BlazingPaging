package com.andreromano.blazingpaging.sample.reddit.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RedditPostResult(
    val title: String,
    val selftext: String,
    val author: String,
    val score: Int,
    val created_utc: Float, // Seconds
    val permalink: String,
    val url: String,
)
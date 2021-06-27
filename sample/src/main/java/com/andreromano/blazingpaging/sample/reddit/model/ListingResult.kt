package com.andreromano.blazingpaging.sample.reddit.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ListingResult(
    val data: Data,
) {
    @JsonClass(generateAdapter = true)
    data class Data(
        val modhash: String,
        val dist: Int,
        val children: List<Child>,
        val before: String?,
        val after: String?,
    )

    @JsonClass(generateAdapter = true)
    data class Child(
        val data: RedditPostResult,
    )
}
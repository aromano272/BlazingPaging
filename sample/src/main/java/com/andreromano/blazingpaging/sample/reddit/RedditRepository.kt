package com.andreromano.blazingpaging.sample.reddit

import com.andreromano.blazingpaging.sample.core.ResultKt
import com.andreromano.blazingpaging.sample.reddit.model.ListingResult
import com.andreromano.blazingpaging.sample.reddit.model.Sort

class RedditRepository(
    private val redditApi: RedditApi,
) {

    suspend fun getListing(
        subreddit: String,
        sort: Sort,
        before: String?,
        after: String?,
        limit: Int,
        count: Int,
    ): ResultKt<ListingResult> =
        redditApi.getListing(
            subreddit,
            sort.toApiValue(),
            before,
            after,
            limit,
            count,
        )

}
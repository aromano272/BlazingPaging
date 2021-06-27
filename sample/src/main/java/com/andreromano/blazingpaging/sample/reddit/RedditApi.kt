package com.andreromano.blazingpaging.sample.reddit

import com.andreromano.blazingpaging.sample.common.core.ResultKt
import com.andreromano.blazingpaging.sample.reddit.model.ListingResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RedditApi {

    @GET("/r/{subreddit}/{sort}/.json")
    suspend fun getListing(
        @Path("subreddit") subreddit: String,
        @Path("sort") sort: String,
        @Query("before") before: String?,
        @Query("after") after: String?,
        @Query("limit") limit: Int, // the maximum number of items to return in this slice of the listing.
        @Query("count") count: Int, // the number of items already fetched, since beginning of this pagination
    ): ResultKt<ListingResult>
}
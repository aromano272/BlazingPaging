package com.andreromano.blazingpaging.sample.reddit

import com.andreromano.blazingpaging.sample.BuildConfig
import okhttp3.Credentials
import retrofit2.http.Field
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.*

interface RedditApi {

    @POST("access_token")
    fun getAccessToken(
        @Header("Authorization") authorization: String = Credentials.basic(BuildConfig.REDDIT_API_KEY, ""),
        @Header("User-Agent") userAgent: String = "android:${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_NAME} (by /u/${BuildConfig.REDDIT_USERNAME})",
        @Field("grant_type") grantType: String = "https://oauth.reddit.com/grants/installed_client",
        @Field("device_id") deviceId: String = UUID.randomUUID().toString(),
    )
}
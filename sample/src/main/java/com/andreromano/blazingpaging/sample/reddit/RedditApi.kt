package com.andreromano.blazingpaging.sample.reddit

import com.andreromano.blazingpaging.sample.BuildConfig
import com.andreromano.blazingpaging.sample.core.ResultKt
import okhttp3.Credentials
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.*

interface RedditApi {

    companion object {
        private const val USER_AGENT: String = "android:${BuildConfig.APPLICATION_ID}:${BuildConfig.VERSION_NAME} (by /u/${BuildConfig.REDDIT_USERNAME})"
    }

    @FormUrlEncoded
    @POST("access_token")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String = Credentials.basic(BuildConfig.REDDIT_API_KEY, ""),
        @Header("User-Agent") userAgent: String = USER_AGENT,
        @Field("grant_type") grantType: String = "https://oauth.reddit.com/grants/installed_client",
        @Field("device_id") deviceId: String = UUID.randomUUID().toString(),
    ): ResultKt<AccessTokenResult>



}
@file:Suppress("RemoveExplicitTypeArguments")

package com.andreromano.blazingpaging.sample.di

import com.andreromano.blazingpaging.sample.BuildConfig
import com.andreromano.blazingpaging.sample.network.mapper.ResultKtCallAdapterFactory
import com.andreromano.blazingpaging.sample.reddit.RedditApi
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import timber.log.Timber

val appNetworkModule = module {

    single<Moshi> { Moshi.Builder().build() }

    single<OkHttpClient> {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        Timber.tag("OkHttp").d(message)
                    }
                }).apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()
    }

    single<Retrofit> { (baseUrl: String) ->
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(MoshiConverterFactory.create(get()))
            .addCallAdapterFactory(ResultKtCallAdapterFactory())
            .client(get())
            .build()
    }

    single<RedditApi> {
        get<Retrofit> { parametersOf(BuildConfig.REDDIT_BASE_URL) }.create(RedditApi::class.java)
    }

}
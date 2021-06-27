@file:Suppress("RemoveExplicitTypeArguments")

package com.andreromano.blazingpaging.sample.common.di

import android.content.Context
import com.andreromano.blazingpaging.sample.BuildConfig
import com.andreromano.blazingpaging.sample.common.database.PreferenceStorage
import com.andreromano.blazingpaging.sample.common.database.SharedPreferenceStorage
import com.andreromano.blazingpaging.sample.common.network.mapper.FromDataToResultKtAdapterFactory
import com.andreromano.blazingpaging.sample.common.network.mapper.ResultKtCallAdapterFactory
import com.andreromano.blazingpaging.sample.reddit.RedditApi
import com.andreromano.blazingpaging.sample.reddit.RedditRepository
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

    single<Moshi> {
        Moshi.Builder()
            .add(FromDataToResultKtAdapterFactory())
            .build()
    }

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

val appDatabaseModule = module {

    single<PreferenceStorage> {
        SharedPreferenceStorage(
            get<Context>().getSharedPreferences("prefs", Context.MODE_PRIVATE),
            get()
        )
    }

}

val appDataModule = module {

    single<RedditRepository> {
        RedditRepository(get())
    }

}
package com.andreromano.blazingpaging.sample

import android.app.Application
import com.andreromano.blazingpaging.sample.common.di.appDataModule
import com.andreromano.blazingpaging.sample.common.di.appDatabaseModule
import com.andreromano.blazingpaging.sample.common.di.appNetworkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                appNetworkModule,
                appDatabaseModule,
                appDataModule,
            )
        }
    }
}
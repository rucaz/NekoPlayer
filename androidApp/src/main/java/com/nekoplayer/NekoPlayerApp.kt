package com.nekoplayer

import android.app.Application
import com.nekoplayer.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class NekoPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@NekoPlayerApp)
            modules(androidModule)
        }
    }
}

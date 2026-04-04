package com.nekoplayer

import android.app.Application
import com.nekoplayer.di.androidModule
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.service.PlaybackServiceManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class NekoPlayerApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidContext(this@NekoPlayerApp)
            modules(
                androidModule,
                appModule  // 额外的应用模块
            )
        }
    }
}

/**
 * 应用级别模块（包含服务管理器）
 */
val appModule = org.koin.dsl.module {
    // 播放服务管理器 - 单例
    single { (get() as Application) }
    single { PlaybackServiceManager(get(), get()) }
}

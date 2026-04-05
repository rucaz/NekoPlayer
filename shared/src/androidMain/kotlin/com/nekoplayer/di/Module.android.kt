package com.nekoplayer.di

import com.nekoplayer.data.api.BiliLoginApi
import com.nekoplayer.data.api.BilibiliApi
import com.nekoplayer.data.api.MiguApi
import com.nekoplayer.data.repository.PlaylistRepository
import com.nekoplayer.data.repository.UserRepository
import com.nekoplayer.database.DriverFactory
import com.nekoplayer.database.NekoDatabase
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.viewmodel.SearchViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android平台Koin模块
 */
val androidModule = module {
    // Settings
    single { Settings() }
    
    // Database
    single { DriverFactory(get()).createDriver() }
    single { NekoDatabase(get()) }
    
    // Repository
    single { UserRepository(get()) }
    single { PlaylistRepository(get()) }
    
    // HTTP Engine
    single<HttpClientEngine> { OkHttp.create() }
    
    // API
    single { BilibiliApi(get()) }
    single { BiliLoginApi(get()) }
    single { MiguApi(get()) }
    
    // ViewModel - 使用single让搜索状态在页面间保持
    single { SearchViewModel(get()) }
    
    // Player - 使用single确保全局唯一实例
    single { AudioPlayer() }
    
    // Queue Manager (单例，全局共享)
    single { QueueManager() }
}

/**
 * Android平台Koin模块（actual实现）
 */
actual fun platformModule(): Module = androidModule

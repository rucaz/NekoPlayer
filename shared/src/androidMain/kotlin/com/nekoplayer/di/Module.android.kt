package com.nekoplayer.di

import com.nekoplayer.data.api.BiliLoginApi
import com.nekoplayer.data.api.BilibiliApi
import com.nekoplayer.data.api.MiguApi
import com.nekoplayer.data.repository.UserRepository
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.ui.viewmodel.SearchViewModel
import com.russhwolf.settings.Settings
import io.ktor.client.engine.okhttp.*
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android平台Koin模块
 */
val androidModule = module {
    // Settings
    single { Settings() }
    
    // Repository
    single { UserRepository(get()) }
    
    // API
    single { BilibiliApi(OkHttp.create()) }
    single { BiliLoginApi(OkHttp.create()) }
    single { MiguApi(OkHttp.create()) }
    
    // ViewModel
    factory { SearchViewModel(get()) }
    
    // Player
    factory { AudioPlayer() }
}

/**
 * Android平台Koin模块（actual实现）
 */
actual fun platformModule(): Module = androidModule

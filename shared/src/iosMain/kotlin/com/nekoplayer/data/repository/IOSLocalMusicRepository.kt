package com.nekoplayer.data.repository

import com.nekoplayer.data.model.LocalSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS 平台本地音乐仓库实现
 * 
 * 使用 MPMediaLibrary 框架访问 iPod 音乐库
 */
class IOSLocalMusicRepository : LocalMusicRepository {
    
    private val _localSongs = MutableStateFlow<List<LocalSong>>(emptyList())
    override fun getLocalSongs(): Flow<List<LocalSong>> = _localSongs.asStateFlow()
    
    override fun hasPermission(): Boolean {
        // iOS 权限检查通过 MPMediaLibrary.authorizationStatus()
        return checkMediaLibraryAuthorization()
    }
    
    override suspend fun requestPermission(): Boolean {
        return requestMediaLibraryAuthorization()
    }
    
    override suspend fun scanMusic(): List<LocalSong> {
        if (!hasPermission()) {
            return emptyList()
        }
        
        // 通过 Objective-C/Swift 桥接访问 MPMediaLibrary
        val songs = queryLocalMusic()
        _localSongs.value = songs
        return songs
    }
    
    override suspend fun refresh() {
        scanMusic()
    }
    
    // 原生方法桥接 (需要在 iOS 项目中实现)
    private fun checkMediaLibraryAuthorization(): Boolean {
        // 通过 expect/actual 或 KMM 桥接调用 iOS 代码
        // 这里使用简化的实现，实际应由平台代码提供
        return true
    }
    
    private suspend fun requestMediaLibraryAuthorization(): Boolean {
        // 实际实现需要调用 MPMediaLibrary.requestAuthorization
        return true
    }
    
    private fun queryLocalMusic(): List<LocalSong> {
        // 实际实现需要通过 MPMediaQuery 查询
        // 这里返回空列表作为占位
        return emptyList()
    }
}

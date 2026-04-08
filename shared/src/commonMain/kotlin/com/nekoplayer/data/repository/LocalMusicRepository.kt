package com.nekoplayer.data.repository

import com.nekoplayer.data.model.LocalSong
import kotlinx.coroutines.flow.Flow

/**
 * 本地音乐仓库接口
 */
interface LocalMusicRepository {
    
    /**
     * 扫描本地音乐
     * @return 扫描到的歌曲列表
     */
    suspend fun scanMusic(): List<LocalSong>
    
    /**
     * 获取已扫描的音乐列表
     */
    fun getLocalSongs(): Flow<List<LocalSong>>
    
    /**
     * 刷新扫描
     */
    suspend fun refresh()
    
    /**
     * 检查是否有存储权限
     */
    fun hasPermission(): Boolean
    
    /**
     * 请求存储权限
     */
    suspend fun requestPermission(): Boolean
}

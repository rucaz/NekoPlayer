package com.nekoplayer.player

import com.nekoplayer.data.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * 音频播放器接口
 * 使用expect/actual模式实现跨平台
 */
expect class AudioPlayer() {
    
    /**
     * 当前播放状态
     */
    val playerState: StateFlow<PlayerState>
    
    /**
     * 当前播放位置（毫秒）
     */
    val currentPosition: StateFlow<Long>
    
    /**
     * 音频波形数据（用于可视化）
     */
    val waveformData: StateFlow<List<Float>>
    
    /**
     * 音频总时长（毫秒）
     */
    val duration: StateFlow<Long>
    
    /**
     * 准备播放
     */
    fun prepare(song: Song)
    
    /**
     * 播放
     */
    fun play()
    
    /**
     * 暂停
     */
    fun pause()
    
    /**
     * 停止
     */
    fun stop()
    
    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Long)
    
    /**
     * 设置音量（0-1）
     */
    fun setVolume(volume: Float)
    
    /**
     * 释放资源
     */
    fun release()
    
    /**
     * 播放下一首（需要设置QueueManager后可用）
     */
    fun playNext()
    
    /**
     * 播放上一首（需要设置QueueManager后可用）
     */
    fun playPrevious()
    
    /**
     * 设置队列管理器
     */
    fun setQueueManager(queueManager: QueueManager)
}

/**
 * 播放器状态
 */
sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(val song: Song) : PlayerState()
    data class Paused(val song: Song) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

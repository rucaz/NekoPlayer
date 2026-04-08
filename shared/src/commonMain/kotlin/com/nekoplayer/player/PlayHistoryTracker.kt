package com.nekoplayer.player

import com.nekoplayer.data.model.PlayerState
import com.nekoplayer.data.repository.PlayHistoryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 播放历史追踪器
 * 
 * 自动监听播放状态，当播放进度超过30%时记录到播放历史
 */
class PlayHistoryTracker(
    private val audioPlayer: AudioPlayer,
    private val playHistoryRepository: PlayHistoryRepository,
    private val coroutineScope: CoroutineScope
) {
    // 当前歌曲已播放时长
    private var currentPlayDuration: Long = 0
    
    // 当前歌曲总时长
    private var currentSongDuration: Long = 0
    
    // 当前歌曲
    private var currentSong: com.nekoplayer.data.model.Song? = null
    
    // 播放会话ID（用于同一session内的去重）
    private var currentSessionId: String = generateSessionId()
    
    // 上次记录时间（避免重复记录）
    private var lastRecordTime: Long = 0
    
    // 记录间隔（5分钟内不重复记录同一首歌）
    private val RECORD_INTERVAL_MS = 5 * 60 * 1000
    
    // 播放进度追踪job
    private var trackingJob: Job? = null
    
    /**
     * 开始追踪
     */
    fun start() {
        // 监听播放状态变化
        coroutineScope.launch {
            audioPlayer.state.collect { state ->
                when (state) {
                    is PlayerState.Playing -> startTracking()
                    is PlayerState.Paused, PlayerState.IDLE -> stopTracking()
                    else -> {}
                }
            }
        }
        
        // 监听歌曲变化
        coroutineScope.launch {
            audioPlayer.currentSong.collect { song ->
                if (song?.id != currentSong?.id) {
                    // 歌曲变化时，先记录上一首的播放
                    recordCurrentPlay()
                    // 重置追踪状态
                    resetTracking(song)
                }
            }
        }
        
        // 监听时长变化
        coroutineScope.launch {
            audioPlayer.duration.collect { duration ->
                currentSongDuration = duration
            }
        }
    }
    
    /**
     * 停止追踪
     */
    fun stop() {
        trackingJob?.cancel()
        recordCurrentPlay()
    }
    
    /**
     * 开始追踪播放时长
     */
    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = coroutineScope.launch {
            while (isActive) {
                delay(1000) // 每秒更新
                currentPlayDuration += 1000
                
                // 检查是否需要自动记录（播放超过30%且未记录过）
                checkAndAutoRecord()
            }
        }
    }
    
    /**
     * 停止追踪播放时长
     */
    private fun stopTracking() {
        trackingJob?.cancel()
        trackingJob = null
    }
    
    /**
     * 重置追踪状态
     */
    private fun resetTracking(song: com.nekoplayer.data.model.Song?) {
        currentSong = song
        currentPlayDuration = 0
        currentSongDuration = 0
        currentSessionId = generateSessionId()
        lastRecordTime = 0
    }
    
    /**
     * 检查并自动记录播放历史
     */
    private suspend fun checkAndAutoRecord() {
        val song = currentSong ?: return
        if (currentSongDuration <= 0) return
        
        val progressPercent = (currentPlayDuration * 100) / currentSongDuration
        
        // 播放超过30%且距离上次记录超过5分钟
        if (progressPercent >= 30 && 
            System.currentTimeMillis() - lastRecordTime > RECORD_INTERVAL_MS) {
            
            recordPlay(song, currentPlayDuration, currentSongDuration)
            lastRecordTime = System.currentTimeMillis()
        }
    }
    
    /**
     * 记录当前播放
     */
    private suspend fun recordCurrentPlay() {
        val song = currentSong ?: return
        if (currentPlayDuration <= 0 || currentSongDuration <= 0) return
        
        recordPlay(song, currentPlayDuration, currentSongDuration)
    }
    
    /**
     * 记录播放
     */
    private suspend fun recordPlay(
        song: com.nekoplayer.data.model.Song,
        playDuration: Long,
        songDuration: Long
    ) {
        try {
            playHistoryRepository.recordPlay(
                song = song,
                playDuration = playDuration,
                songDuration = songDuration,
                sessionId = currentSessionId
            )
        } catch (e: Exception) {
            // 记录失败不中断播放
            e.printStackTrace()
        }
    }
    
    /**
     * 手动记录播放（用于用户主动点击播放）
     */
    suspend fun manualRecord(song: com.nekoplayer.data.model.Song) {
        recordPlay(song, playDuration = 0, songDuration = song.duration)
    }
    
    companion object {
        /**
         * 生成会话ID
         */
        fun generateSessionId(): String {
            return "${System.currentTimeMillis()}-${(0..9999).random()}"
        }
    }
}

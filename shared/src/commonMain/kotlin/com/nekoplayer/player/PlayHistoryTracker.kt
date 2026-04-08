package com.nekoplayer.player

import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.PlayHistoryRepository
import com.nekoplayer.data.repository.StatsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 播放历史追踪器
 * 
 * 监听播放器状态，自动记录播放历史和统计数据：
 * - 播放进度超过30%时记录
 * - 同一首歌5分钟内不重复记录
 * - 支持播放会话追踪
 * - 自动更新播放统计（每日/艺术家/歌曲）
 */
class PlayHistoryTracker(
    private val audioPlayer: AudioPlayer,
    private val playHistoryRepository: PlayHistoryRepository,
    private val statsRepository: StatsRepository? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // 当前播放会话
    private var currentSession: PlaySession? = null
    
    // 已记录的历史（用于去重）
    private val recentRecords = mutableMapOf<String, Long>()
    
    // 去重时间窗口（5分钟）
    private val dedupWindowMs = 5 * 60 * 1000L
    
    // 记录阈值（30%）
    private val recordThresholdPercent = 30
    
    init {
        startTracking()
    }
    
    /**
     * 开始追踪播放状态
     */
    private fun startTracking() {
        // 监听播放器状态变化
        audioPlayer.playerState
            .distinctUntilChanged()
            .onEach { state ->
                when (state) {
                    is PlayerState.Playing -> {
                        startNewSession(state.song)
                    }
                    is PlayerState.Paused -> {
                        currentSession?.pause()
                    }
                    is PlayerState.Idle,
                    is PlayerState.Error -> {
                        endCurrentSession()
                    }
                    else -> {}
                }
            }
            .launchIn(scope)
        
        // 监听播放进度
        combine(
            audioPlayer.currentPosition,
            audioPlayer.duration,
            audioPlayer.playerState
        ) { position, duration, state ->
            Triple(position, duration, state)
        }
            .filter { (_, duration, state) ->
                // 只处理有有效时长的播放状态
                duration > 0 && state is PlayerState.Playing
            }
            .onEach { (position, duration, _) ->
                currentSession?.updateProgress(position, duration)
                
                // 检查是否达到记录阈值
                val percent = ((position * 100) / duration).toInt()
                if (percent >= recordThresholdPercent) {
                    tryRecordHistory()
                }
            }
            .launchIn(scope)
    }
    
    /**
     * 开始新的播放会话
     */
    private fun startNewSession(song: Song) {
        // 如果之前有会话，先结束它
        endCurrentSession()
        
        currentSession = PlaySession(
            song = song,
            startTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 结束当前会话
     */
    private fun endCurrentSession() {
        currentSession?.let { session ->
            session.end()
            
            // 如果已经达到阈值，记录历史
            if (session.hasReachedThreshold(recordThresholdPercent)) {
                scope.launch {
                    recordPlayHistory(session)
                }
            }
        }
        currentSession = null
    }
    
    /**
     * 尝试记录播放历史（带去重）
     */
    private suspend fun tryRecordHistory() {
        val session = currentSession ?: return
        val song = session.song
        
        // 检查是否已经记录过（去重）
        val lastRecordTime = recentRecords[song.id]
        val now = System.currentTimeMillis()
        
        if (lastRecordTime != null && (now - lastRecordTime) < dedupWindowMs) {
            // 5分钟内已记录过，跳过
            return
        }
        
        // 标记为已记录
        recentRecords[song.id] = now
        
        // 清理过期的去重记录
        cleanupRecentRecords()
        
        // 记录到数据库
        recordPlayHistory(session)
    }
    
    /**
     * 记录播放历史到数据库（同时更新统计）
     */
    private suspend fun recordPlayHistory(session: PlaySession) {
        try {
            val playDuration = session.getPlayDuration()
            val playedAt = System.currentTimeMillis()
            
            // 1. 记录播放历史
            playHistoryRepository.recordPlay(
                song = session.song,
                playDuration = playDuration,
                songDuration = session.songDuration,
                sessionId = session.sessionId
            )
            
            // 2. 更新播放统计（如果可用）
            statsRepository?.recordPlay(
                song = session.song,
                playDuration = playDuration,
                playedAt = playedAt
            )
        } catch (e: Exception) {
            // 记录失败不中断播放
            e.printStackTrace()
        }
    }
    
    /**
     * 清理过期的去重记录
     */
    private fun cleanupRecentRecords() {
        val now = System.currentTimeMillis()
        recentRecords.entries.removeIf { (_, timestamp) ->
            (now - timestamp) > dedupWindowMs
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        endCurrentSession()
        recentRecords.clear()
    }
    
    /**
     * 播放会话
     */
    private data class PlaySession(
        val song: Song,
        val startTime: Long,
        val sessionId: String = java.util.UUID.randomUUID().toString()
    ) {
        var endTime: Long? = null
            private set
        
        var songDuration: Long = 0
            private set
        
        private var maxProgress: Long = 0
        private var pauseStartTime: Long? = null
        private var totalPauseDuration: Long = 0
        
        fun updateProgress(position: Long, duration: Long) {
            maxProgress = maxOf(maxProgress, position)
            songDuration = duration
        }
        
        fun pause() {
            if (pauseStartTime == null) {
                pauseStartTime = System.currentTimeMillis()
            }
        }
        
        fun resume() {
            pauseStartTime?.let { start ->
                totalPauseDuration += System.currentTimeMillis() - start
                pauseStartTime = null
            }
        }
        
        fun end() {
            endTime = System.currentTimeMillis()
            pauseStartTime?.let { start ->
                totalPauseDuration += System.currentTimeMillis() - start
                pauseStartTime = null
            }
        }
        
        fun getPlayDuration(): Long {
            val end = endTime ?: System.currentTimeMillis()
            return (end - startTime - totalPauseDuration).coerceAtLeast(0)
        }
        
        fun hasReachedThreshold(thresholdPercent: Int): Boolean {
            if (songDuration <= 0) return false
            val percent = ((maxProgress * 100) / songDuration).toInt()
            return percent >= thresholdPercent
        }
    }
}

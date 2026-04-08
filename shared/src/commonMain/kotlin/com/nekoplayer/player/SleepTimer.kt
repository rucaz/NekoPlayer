package com.nekoplayer.player

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 睡眠定时器
 * 
 * 支持设置倒计时，到时自动暂停播放
 * 提供剩余时间显示和取消功能
 */
class SleepTimer(
    private val audioPlayer: AudioPlayer,
    private val coroutineScope: CoroutineScope
) {
    // 定时器选项（分钟）
    companion object {
        val OPTIONS = listOf(15, 30, 45, 60)
        const val DEFAULT_OPTION = 30
    }
    
    // 定时器状态
    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()
    
    // 剩余时间（毫秒）
    private val _remainingMillis = MutableStateFlow(0L)
    val remainingMillis: StateFlow<Long> = _remainingMillis.asStateFlow()
    
    // 定时器Job
    private var timerJob: Job? = null
    
    // 原始音量（用于淡入淡出）
    private var originalVolume: Float = 1.0f
    
    /**
     * 定时器状态
     */
    sealed class TimerState {
        object Idle : TimerState()
        data class Running(val totalMinutes: Int) : TimerState()
        object Finished : TimerState()
    }
    
    /**
     * 设置定时器
     * @param minutes 定时分钟数
     */
    fun setTimer(minutes: Int) {
        cancelTimer()
        
        val totalMillis = minutes * 60 * 1000L
        _remainingMillis.value = totalMillis
        _state.value = TimerState.Running(minutes)
        
        originalVolume = 1.0f // 假设当前音量为最大
        
        timerJob = coroutineScope.launch {
            val startTime = System.currentTimeMillis()
            val endTime = startTime + totalMillis
            
            while (isActive && System.currentTimeMillis() < endTime) {
                val remaining = endTime - System.currentTimeMillis()
                _remainingMillis.value = remaining.coerceAtLeast(0)
                
                // 最后30秒开始淡出
                if (remaining <= 30_000 && remaining > 0) {
                    val fadeVolume = remaining / 30_000f
                    audioPlayer.setVolume(fadeVolume.coerceIn(0f, 1f))
                }
                
                delay(1000) // 每秒更新一次
            }
            
            // 定时结束
            if (isActive) {
                _remainingMillis.value = 0
                _state.value = TimerState.Finished
                audioPlayer.pause()
                audioPlayer.setVolume(originalVolume) // 恢复音量
            }
        }
    }
    
    /**
     * 取消定时器
     */
    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.value = TimerState.Idle
        _remainingMillis.value = 0
        audioPlayer.setVolume(originalVolume) // 恢复音量
    }
    
    /**
     * 添加更多时间
     * @param minutes 要添加的分钟数
     */
    fun extendTime(minutes: Int) {
        val currentState = _state.value
        if (currentState is TimerState.Running) {
            val additionalMillis = minutes * 60 * 1000L
            val newRemaining = _remainingMillis.value + additionalMillis
            val newTotalMinutes = (newRemaining / 60_000).toInt() + 1
            
            cancelTimer()
            setTimer(newTotalMinutes.coerceAtMost(120)) // 最多2小时
        }
    }
    
    /**
     * 格式化剩余时间
     */
    fun formatRemainingTime(millis: Long): String {
        val minutes = millis / 60_000
        val seconds = (millis % 60_000) / 1000
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * 释放资源
     */
    fun release() {
        cancelTimer()
    }
}

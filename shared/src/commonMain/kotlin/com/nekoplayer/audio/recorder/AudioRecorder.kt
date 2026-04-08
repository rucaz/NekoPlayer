package com.nekoplayer.audio.recorder

import kotlinx.coroutines.flow.Flow

/**
 * 音频录音器接口
 * 
 * 跨平台录音抽象，支持实时音频流
 */
interface AudioRecorder {
    
    /**
     * 录音状态
     */
    enum class State {
        IDLE,       // 空闲
        RECORDING,  // 录音中
        PAUSED,     // 暂停
        STOPPED     // 已停止
    }
    
    /**
     * 录音配置
     */
    data class Config(
        val sampleRate: Int = 16000,        // 采样率
        val channelConfig: Int = 1,          // 单声道=1，立体声=2
        val audioFormat: Int = 16,           // 16bit PCM
        val bufferSize: Int = 1024           // 缓冲区大小
    )
    
    /**
     * 录音数据块
     */
    data class AudioChunk(
        val data: ShortArray,                // PCM数据
        val timestamp: Long,                 // 时间戳
        val sampleRate: Int                  // 采样率
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AudioChunk) return false
            return timestamp == other.timestamp && 
                   sampleRate == other.sampleRate &&
                   data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + timestamp.hashCode()
            result = 31 * result + sampleRate
            return result
        }
    }
    
    /**
     * 当前状态流
     */
    val state: Flow<State>
    
    /**
     * 实时音频流
     */
    val audioStream: Flow<AudioChunk>
    
    /**
     * 录音时长（毫秒）
     */
    val durationMs: Flow<Long>
    
    /**
     * 开始录音
     */
    suspend fun start(config: Config = Config()): Result<Unit>
    
    /**
     * 停止录音
     * @return 录音文件路径（如果配置了保存）
     */
    suspend fun stop(): Result<String?>
    
    /**
     * 暂停录音
     */
    suspend fun pause(): Result<Unit>
    
    /**
     * 恢复录音
     */
    suspend fun resume(): Result<Unit>
    
    /**
     * 释放资源
     */
    fun release()
}

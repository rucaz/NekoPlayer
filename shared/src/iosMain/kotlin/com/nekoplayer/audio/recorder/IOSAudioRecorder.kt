package com.nekoplayer.audio.recorder

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS 平台音频录音器占位实现
 * 
 * 实际实现需要使用 AVAudioRecorder
 */
class IOSAudioRecorder : AudioRecorder {
    
    private val _state = MutableStateFlow(AudioRecorder.State.IDLE)
    override val state: Flow<AudioRecorder.State> = _state.asStateFlow()
    
    private val _audioStream = MutableStateFlow<AudioRecorder.AudioChunk?>(null)
    override val audioStream: Flow<AudioRecorder.AudioChunk> = 
        throw NotImplementedError("iOS audio recording not yet implemented")
    
    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Flow<Long> = _durationMs.asStateFlow()
    
    override suspend fun start(config: AudioRecorder.Config): Result<Unit> {
        // TODO: 使用 AVAudioRecorder 实现
        return Result.failure(NotImplementedError("iOS audio recording not yet implemented"))
    }
    
    override suspend fun stop(): Result<String?> {
        return Result.success(null)
    }
    
    override suspend fun pause(): Result<Unit> {
        return Result.failure(NotImplementedError("iOS audio recording not yet implemented"))
    }
    
    override suspend fun resume(): Result<Unit> {
        return Result.failure(NotImplementedError("iOS audio recording not yet implemented"))
    }
    
    override fun release() {
        // TODO: 清理资源
    }
}

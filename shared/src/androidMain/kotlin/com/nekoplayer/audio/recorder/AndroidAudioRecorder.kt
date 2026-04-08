package com.nekoplayer.audio.recorder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 平台音频录音器实现
 * 
 * 使用 AudioRecord API 实现低延迟实时音频采集
 * 支持边录音边处理（用于哼唱识别）
 */
class AndroidAudioRecorder(
    private val context: Context
) : AudioRecorder {
    
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0
    
    // 配置
    private var currentConfig: AudioRecorder.Config = AudioRecorder.Config()
    private var outputFile: File? = null
    
    // 状态管理
    private val _state = MutableStateFlow(AudioRecorder.State.IDLE)
    override val state: Flow<AudioRecorder.State> = _state.asStateFlow()
    
    // 音频流
    private val _audioStream = MutableSharedFlow<AudioRecorder.AudioChunk>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val audioStream: Flow<AudioRecorder.AudioChunk> = _audioStream.asSharedFlow()
    
    // 录音时长
    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Flow<Long> = _durationMs.asStateFlow()
    
    // 定时更新时长
    private var durationJob: Job? = null
    
    override suspend fun start(config: AudioRecorder.Config): Result<Unit> {
        // 检查权限
        if (!hasRecordPermission()) {
            return Result.failure(SecurityException("Missing RECORD_AUDIO permission"))
        }
        
        // 检查当前状态
        if (_state.value == AudioRecorder.State.RECORDING) {
            return Result.failure(IllegalStateException("Already recording"))
        }
        
        currentConfig = config
        
        // 计算缓冲区大小
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            if (config.channelConfig == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, config.bufferSize * 2)
        
        // 创建 AudioRecord
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            if (config.channelConfig == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            return Result.failure(IllegalStateException("AudioRecord initialization failed"))
        }
        
        // 创建输出文件（可选）
        outputFile = File(context.cacheDir, "recording_${System.currentTimeMillis()}.pcm")
        
        // 开始录音
        try {
            audioRecord?.startRecording()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        
        _state.value = AudioRecorder.State.RECORDING
        startTime = System.currentTimeMillis()
        pausedDuration = 0
        
        // 启动录音协程
        recordingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            
            val buffer = ShortArray(config.bufferSize)
            val byteBuffer = ByteBuffer.allocateDirect(config.bufferSize * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
            
            FileOutputStream(outputFile).use { fos ->
                while (isActive && _state.value == AudioRecorder.State.RECORDING) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    
                    if (readSize > 0) {
                        val timestamp = System.currentTimeMillis()
                        
                        // 发送音频流到上游（用于实时处理）
                        val chunk = AudioRecorder.AudioChunk(
                            data = buffer.copyOf(readSize),
                            timestamp = timestamp,
                            sampleRate = config.sampleRate
                        )
                        _audioStream.tryEmit(chunk)
                        
                        // 写入文件（可选，用于调试）
                        byteBuffer.clear()
                        for (i in 0 until readSize) {
                            byteBuffer.putShort(buffer[i])
                        }
                        byteBuffer.flip()
                        fos.write(byteBuffer.array(), 0, readSize * 2)
                    }
                }
            }
        }
        
        // 启动时长更新
        durationJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (_state.value == AudioRecorder.State.RECORDING) {
                    val current = System.currentTimeMillis()
                    _durationMs.value = current - startTime - pausedDuration
                }
                delay(100)
            }
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun stop(): Result<String?> {
        if (_state.value != AudioRecorder.State.RECORDING && _state.value != AudioRecorder.State.PAUSED) {
            return Result.success(null)
        }
        
        _state.value = AudioRecorder.State.STOPPED
        
        // 停止录音
        recordingJob?.cancelAndJoin()
        recordingJob = null
        
        durationJob?.cancel()
        durationJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
        
        val filePath = outputFile?.absolutePath
        outputFile = null
        
        // 重置状态
        _durationMs.value = 0
        
        return Result.success(filePath)
    }
    
    override suspend fun pause(): Result<Unit> {
        if (_state.value != AudioRecorder.State.RECORDING) {
            return Result.failure(IllegalStateException("Not recording"))
        }
        
        _state.value = AudioRecorder.State.PAUSED
        pauseStartTime = System.currentTimeMillis()
        
        // 暂停 AudioRecord（Android API 24+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            audioRecord?.stop()
        }
        
        return Result.success(Unit)
    }
    
    override suspend fun resume(): Result<Unit> {
        if (_state.value != AudioRecorder.State.PAUSED) {
            return Result.failure(IllegalStateException("Not paused"))
        }
        
        pausedDuration += System.currentTimeMillis() - pauseStartTime
        _state.value = AudioRecorder.State.RECORDING
        
        // 恢复 AudioRecord（Android API 24+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            audioRecord?.startRecording()
        }
        
        return Result.success(Unit)
    }
    
    override fun release() {
        recordingJob?.cancel()
        durationJob?.cancel()
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // ignore
        }
        
        audioRecord?.release()
        audioRecord = null
        
        outputFile?.delete()
        outputFile = null
        
        _state.value = AudioRecorder.State.IDLE
    }
    
    /**
     * 检查录音权限
     */
    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取所需权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
}

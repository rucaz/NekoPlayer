package com.nekoplayer.player

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nekoplayer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android平台音频播放器实现（使用ExoPlayer）
 */
actual class AudioPlayer : KoinComponent {
    
    private val context: Context by inject()
    
    private var exoPlayer: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    actual val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _waveformData = MutableStateFlow(List(64) { 0f })
    actual val waveformData: StateFlow<List<Float>> = _waveformData.asStateFlow()
    
    private var currentSong: Song? = null
    private var queueManager: QueueManager? = null
    
    // 位置更新任务
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration.coerceAtLeast(0L)
            }
            handler.postDelayed(this, 200) // 200ms更新一次
        }
    }
    
    actual fun prepare(song: Song) {
        currentSong = song
        _playerState.value = PlayerState.Loading
        
        release() // 释放之前的播放器
        
        val playUrl = song.playUrl
        if (playUrl.isNullOrEmpty()) {
            _playerState.value = PlayerState.Error("无效的播放链接")
            return
        }
        
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(playUrl))
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            _duration.value = duration.coerceAtLeast(0L)
                            _playerState.value = PlayerState.Playing(song)
                            setupVisualizer(audioSessionId)
                            startPositionUpdates()
                        }
                        Player.STATE_ENDED -> {
                            // 自动播放下一首
                            playNext()
                        }
                        Player.STATE_BUFFERING -> {
                            _playerState.value = PlayerState.Loading
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    currentSong?.let { song ->
                        _playerState.value = if (isPlaying) {
                            PlayerState.Playing(song)
                        } else {
                            PlayerState.Paused(song)
                        }
                    }
                    if (isPlaying) startPositionUpdates() else stopPositionUpdates()
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _playerState.value = PlayerState.Error(error.message ?: "播放错误")
                    stopPositionUpdates()
                }
            })
            
            prepare()
        }
    }
    
    actual fun play() {
        exoPlayer?.play()
    }
    
    actual fun pause() {
        exoPlayer?.pause()
    }
    
    actual fun stop() {
        exoPlayer?.stop()
        stopPositionUpdates()
        _playerState.value = PlayerState.Idle
    }
    
    actual fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _currentPosition.value = position
    }
    
    actual fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }
    
    actual fun release() {
        stopPositionUpdates()
        try {
            visualizer?.enabled = false
            visualizer?.release()
            visualizer = null
        } catch (_: Exception) {}
        
        exoPlayer?.release()
        exoPlayer = null
    }
    
    /**
     * 播放下一首
     */
    actual fun playNext() {
        queueManager?.playNext()?.let { nextSong ->
            prepare(nextSong)
            play()
        } ?: run {
            // 没有下一首，停止播放
            stop()
        }
    }
    
    /**
     * 播放上一首
     */
    actual fun playPrevious() {
        queueManager?.playPrevious()?.let { prevSong ->
            prepare(prevSong)
            play()
        }
    }
    
    /**
     * 设置队列管理器
     */
    actual fun setQueueManager(queueManager: QueueManager) {
        this.queueManager = queueManager
    }
    
    private fun startPositionUpdates() {
        handler.removeCallbacks(positionUpdateRunnable)
        handler.post(positionUpdateRunnable)
    }
    
    private fun stopPositionUpdates() {
        handler.removeCallbacks(positionUpdateRunnable)
    }
    
    /**
     * 设置音频可视化
     */
    private fun setupVisualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer,
                        waveform: ByteArray,
                        samplingRate: Int
                    ) {
                        // 将波形数据转换为Float（-1到1）
                        val data = waveform.map { (it.toInt() and 0xFF) / 128f - 1f }
                        _waveformData.value = data.take(64)
                    }
                    
                    override fun onFftDataCapture(
                        visualizer: Visualizer,
                        fft: ByteArray,
                        samplingRate: Int
                    ) {
                        // FFT数据用于频谱显示
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            // Visualizer可能不支持，忽略错误
        }
    }
}

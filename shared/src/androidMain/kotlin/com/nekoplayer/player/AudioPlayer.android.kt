package com.nekoplayer.player

import android.media.audiofx.Visualizer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nekoplayer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android平台音频播放器实现（使用ExoPlayer）
 */
actual class AudioPlayer {
    
    private var exoPlayer: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _waveformData = MutableStateFlow(List(64) { 0f })
    actual val waveformData: StateFlow<List<Float>> = _waveformData.asStateFlow()
    
    private var currentSong: Song? = null
    
    actual fun prepare(song: Song) {
        currentSong = song
        _playerState.value = PlayerState.Loading
        
        val context = getApplicationContext()
        exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(song.playUrl ?: return))
            prepare()
            
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            _playerState.value = PlayerState.Playing(song)
                            setupVisualizer(audioSessionId)
                        }
                        Player.STATE_ENDED -> {
                            _playerState.value = PlayerState.Idle
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
                }
                
                override fun onPlayerError(error: Player.PlayerError) {
                    _playerState.value = PlayerState.Error(error.message ?: "Unknown error")
                }
            })
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
        _playerState.value = PlayerState.Idle
    }
    
    actual fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    actual fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
    }
    
    actual fun release() {
        visualizer?.release()
        exoPlayer?.release()
        exoPlayer = null
    }
    
    /**
     * 设置音频可视化
     */
    private fun setupVisualizer(audioSessionId: Int) {
        if (audioSessionId == 0) return
        
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
    }
    
    private fun getApplicationContext(): android.content.Context {
        // 实际实现中通过依赖注入或全局应用类获取
        throw NotImplementedError("Need to provide context via DI")
    }
}

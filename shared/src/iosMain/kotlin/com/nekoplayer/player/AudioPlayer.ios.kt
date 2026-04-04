package com.nekoplayer.player

import com.nekoplayer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.*
import platform.Foundation.NSURL

/**
 * iOS平台音频播放器实现（使用AVPlayer）
 * 
 * 注意：这是基础实现，完整功能需要更多Objective-C桥接
 * 波形可视化需要使用MTAudioProcessingTap或AudioUnit
 */
actual class AudioPlayer {
    
    private var avPlayer: AVPlayer? = null
    
    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    actual val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    actual val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    // iOS波形数据需要使用MTAudioProcessingTap获取
    // 这里先用模拟数据
    private val _waveformData = MutableStateFlow(List(64) { 0f })
    actual val waveformData: StateFlow<List<Float>> = _waveformData.asStateFlow()
    
    private var currentSong: Song? = null
    private var timeObserver: Any? = null
    
    actual fun prepare(song: Song) {
        currentSong = song
        _playerState.value = PlayerState.Loading
        
        song.playUrl?.let { urlString ->
            val url = NSURL(string = urlString)
            val playerItem = AVPlayerItem(url = url)
            
            avPlayer = AVPlayer(playerItem = playerItem)
            
            // 监听播放状态
            playerItem.addObserver(
                this,
                forKeyPath = "status",
                options = NSKeyValueObservingOptionNew,
                context = null
            )
            
            // 添加时间观察器
            val interval = CMTimeMakeWithSeconds(0.1, preferredTimescale = 1000)
            timeObserver = avPlayer?.addPeriodicTimeObserverForInterval(
                interval,
                queue = null,
                usingBlock = { time ->
                    val seconds = CMTimeGetSeconds(time)
                    _currentPosition.value = (seconds * 1000).toLong()
                }
            )
        }
    }
    
    actual fun play() {
        avPlayer?.play()
        currentSong?.let { _playerState.value = PlayerState.Playing(it) }
    }
    
    actual fun pause() {
        avPlayer?.pause()
        currentSong?.let { _playerState.value = PlayerState.Paused(it) }
    }
    
    actual fun stop() {
        avPlayer?.pause()
        avPlayer?.seekToTime(CMTime.zero())
        _playerState.value = PlayerState.Idle
    }
    
    actual fun seekTo(position: Long) {
        val time = CMTimeMakeWithSeconds(
            seconds = position / 1000.0,
            preferredTimescale = 1000
        )
        avPlayer?.seekToTime(time)
    }
    
    actual fun setVolume(volume: Float) {
        avPlayer?.volume = volume
    }
    
    actual fun release() {
        timeObserver?.let { observer ->
            avPlayer?.removeTimeObserver(observer)
        }
        avPlayer = null
    }
    
    // KVO回调（简化版，实际需要完整实现）
    fun observeValueForKeyPath(
        keyPath: String?,
        ofObject: Any?,
        change: Map<Any?, *>?,
        context: kotlinx.cinterop.COpaquePointer?
    ) {
        if (keyPath == "status") {
            val playerItem = ofObject as? AVPlayerItem
            when (playerItem?.status) {
                AVPlayerItemStatusReadyToPlay -> {
                    currentSong?.let { _playerState.value = PlayerState.Playing(it) }
                }
                AVPlayerItemStatusFailed -> {
                    _playerState.value = PlayerState.Error("Failed to load")
                }
                else -> {}
            }
        }
    }
}

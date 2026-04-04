package com.nekoplayer.service

import android.content.Context
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 播放服务管理器
 * 管理后台播放服务的生命周期
 */
class PlaybackServiceManager(
    private val context: Context,
    private val player: AudioPlayer
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var isServiceRunning = false
    
    init {
        // 监听播放状态，自动管理服务
        scope.launch {
            player.playerState.collectLatest { state ->
                when (state) {
                    is PlayerState.Playing -> {
                        if (!isServiceRunning) {
                            MusicPlaybackService.start(context, state.song)
                            isServiceRunning = true
                        }
                    }
                    is PlayerState.Paused -> {
                        // 暂停时保持服务运行，通知栏显示暂停状态
                    }
                    is PlayerState.Idle, is PlayerState.Error -> {
                        if (isServiceRunning) {
                            MusicPlaybackService.stop(context)
                            isServiceRunning = false
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    
    fun release() {
        scope.cancel()
        if (isServiceRunning) {
            MusicPlaybackService.stop(context)
            isServiceRunning = false
        }
    }
}

package com.nekoplayer.data.repository

import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 播放状态自动保存器
 * 监听播放状态变化并自动保存到数据库
 */
class PlaybackStateSaver(
    private val playbackRepository: PlaybackRepository,
    private val audioPlayer: AudioPlayer,
    private val queueManager: QueueManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var saveJob: Job? = null

    /**
     * 开始自动保存
     */
    fun start() {
        // 监听播放状态变化，延迟保存以避免频繁写入
        scope.launch {
            combine(
                queueManager.currentQueue,
                queueManager.currentSong,
                audioPlayer.currentPosition,
                queueManager.playMode
            ) { queue, currentSong, position, playMode ->
                Quadruple(queue, currentSong, position, playMode)
            }.collectLatest { (queue, currentSong, position, playMode) ->
                // 延迟 2 秒后保存，避免频繁写入
                saveJob?.cancel()
                saveJob = scope.launch {
                    delay(2000)
                    playbackRepository.savePlaybackState(
                        queue = queue,
                        currentSong = currentSong,
                        currentPosition = position,
                        playMode = playMode
                    )
                }
            }
        }
    }

    /**
     * 停止自动保存
     */
    fun stop() {
        saveJob?.cancel()
        saveJob = null
    }

    /**
     * 立即保存当前状态
     */
    fun saveImmediately() {
        scope.launch {
            playbackRepository.savePlaybackState(
                queue = queueManager.currentQueue.value,
                currentSong = queueManager.currentSong.value,
                currentPosition = audioPlayer.currentPosition.value,
                playMode = queueManager.playMode.value
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

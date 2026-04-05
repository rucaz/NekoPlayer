package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import com.nekoplayer.player.QueueManager
import com.nekoplayer.utils.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 播放状态仓库
 * 用于持久化播放队列和当前播放状态
 */
class PlaybackRepository(
    private val database: NekoDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _restoredState = MutableStateFlow<RestoredPlaybackState?>(null)
    val restoredState: StateFlow<RestoredPlaybackState?> = _restoredState.asStateFlow()

    /**
     * 保存播放状态
     */
    suspend fun savePlaybackState(
        queue: List<Song>,
        currentSong: Song?,
        currentPosition: Long,
        playMode: QueueManager.PlayMode
    ) = withContext(Dispatchers.Default) {
        try {
            // 保存状态
            val existingState = database.playbackStateQueries.getState().executeAsOneOrNull()
            val modeString = playMode.name
            
            if (existingState != null) {
                database.playbackStateQueries.updateState(
                    currentSongId = currentSong?.id,
                    currentPosition = currentPosition,
                    playMode = modeString,
                    updatedAt = currentTimeMillis()
                )
            } else {
                database.playbackStateQueries.insertState(
                    currentSongId = currentSong?.id,
                    currentPosition = currentPosition,
                    playMode = modeString,
                    updatedAt = currentTimeMillis()
                )
            }

            // 清空旧队列并保存新队列
            database.playbackStateQueries.clearQueue()
            queue.forEachIndexed { index, song ->
                database.playbackStateQueries.insertQueueItem(
                    id = generateId(),
                    songId = song.id,
                    songJson = json.encodeToString(song),
                    order = index.toLong(),
                    addedAt = currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 加载播放状态
     */
    suspend fun loadPlaybackState(): RestoredPlaybackState? = withContext(Dispatchers.Default) {
        try {
            val state = database.playbackStateQueries.getState().executeAsOneOrNull() ?: return@withContext null
            val queueItems = database.playbackStateQueries.getQueue().executeAsList()
            
            val queue = queueItems.mapNotNull { item ->
                try {
                    json.decodeFromString<Song>(item.songJson)
                } catch (e: Exception) {
                    null
                }
            }
            
            val playMode = try {
                QueueManager.PlayMode.valueOf(state.playMode)
            } catch (e: Exception) {
                QueueManager.PlayMode.SEQUENTIAL
            }
            
            RestoredPlaybackState(
                queue = queue,
                currentSongId = state.currentSongId,
                currentPosition = state.currentPosition,
                playMode = playMode
            ).also {
                _restoredState.value = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 清除保存的播放状态
     */
    suspend fun clearPlaybackState() = withContext(Dispatchers.Default) {
        try {
            database.playbackStateQueries.clearQueue()
            // 删除状态记录
            // 注意：PlaybackState 表需要添加 delete 操作
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateId(): String {
        return "${currentTimeMillis()}_${(0..9999).random()}"
    }
}

/**
 * 恢复的播放状态
 */
data class RestoredPlaybackState(
    val queue: List<Song>,
    val currentSongId: String?,
    val currentPosition: Long,
    val playMode: QueueManager.PlayMode
)

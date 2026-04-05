package com.nekoplayer.player

import com.nekoplayer.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 播放队列管理器
 * 管理当前播放队列，支持顺序/随机/单曲循环模式
 * 队列存储在内存中，不持久化
 */
class QueueManager {

    /**
     * 播放模式
     */
    enum class PlayMode {
        SEQUENTIAL,  // 顺序播放
        SHUFFLE,     // 随机播放
        REPEAT_ONE   // 单曲循环
    }

    // 当前队列
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    // 当前播放索引
    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // 当前歌曲
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    // 播放模式
    private val _playMode = MutableStateFlow(PlayMode.SEQUENTIAL)
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    // 随机播放历史（用于返回上一首）
    private val shuffleHistory = mutableListOf<Int>()

    // 原始顺序队列（随机模式时用于恢复）
    private var originalQueue: List<Song> = emptyList()

    /**
     * 设置播放队列并开始播放
     * @param songs 歌曲列表
     * @param startIndex 开始播放的索引
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return

        originalQueue = songs.toList()

        // 如果是随机模式，打乱队列
        _currentQueue.value = if (_playMode.value == PlayMode.SHUFFLE) {
            songs.shuffled()
        } else {
            songs.toList()
        }

        val validIndex = startIndex.coerceIn(0, songs.size - 1)
        _currentIndex.value = validIndex
        _currentSong.value = _currentQueue.value.getOrNull(validIndex)
        shuffleHistory.clear()
        shuffleHistory.add(validIndex)
    }

    /**
     * 添加歌曲到队列末尾
     */
    fun addToQueue(song: Song) {
        val newQueue = _currentQueue.value.toMutableList()
        newQueue.add(song)
        _currentQueue.value = newQueue

        // 同时更新原始队列
        if (_playMode.value == PlayMode.SHUFFLE) {
            originalQueue = originalQueue + song
        }
    }

    /**
     * 插入歌曲到下一首播放
     */
    fun addToQueueNext(song: Song) {
        val currentIdx = _currentIndex.value
        if (currentIdx < 0) {
            // 队列空，直接播放
            playQueue(listOf(song))
            return
        }

        val newQueue = _currentQueue.value.toMutableList()
        // 插入到当前索引+1的位置
        newQueue.add(currentIdx + 1, song)
        _currentQueue.value = newQueue
    }

    /**
     * 播放下一首
     * @return 下一首歌曲，如果队列结束返回 null
     */
    fun playNext(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null

        // 单曲循环模式
        if (_playMode.value == PlayMode.REPEAT_ONE) {
            return _currentSong.value
        }

        val nextIndex = _currentIndex.value + 1

        return if (nextIndex < queue.size) {
            _currentIndex.value = nextIndex
            val song = queue[nextIndex]
            _currentSong.value = song
            shuffleHistory.add(nextIndex)
            song
        } else {
            // 队列结束，返回 null（可以触发自动停止或循环）
            null
        }
    }

    /**
     * 播放上一首
     * @return 上一首歌曲，如果没有历史返回 null
     */
    fun playPrevious(): Song? {
        val queue = _currentQueue.value
        if (queue.isEmpty()) return null

        // 单曲循环模式
        if (_playMode.value == PlayMode.REPEAT_ONE) {
            return _currentSong.value
        }

        // 从历史记录返回
        if (shuffleHistory.size > 1) {
            shuffleHistory.removeAt(shuffleHistory.size - 1)
            val prevIndex = shuffleHistory.last()
            _currentIndex.value = prevIndex
            val song = queue[prevIndex]
            _currentSong.value = song
            return song
        }

        // 没有历史了，返回当前歌曲
        return _currentSong.value
    }

    /**
     * 跳转到指定歌曲
     */
    fun skipToSong(index: Int): Song? {
        val queue = _currentQueue.value
        if (index < 0 || index >= queue.size) return null

        _currentIndex.value = index
        val song = queue[index]
        _currentSong.value = song
        shuffleHistory.add(index)
        return song
    }

    /**
     * 从队列移除歌曲
     */
    fun removeFromQueue(index: Int) {
        val queue = _currentQueue.value.toMutableList()
        if (index < 0 || index >= queue.size) return

        queue.removeAt(index)
        _currentQueue.value = queue

        // 调整当前索引
        if (index < _currentIndex.value) {
            _currentIndex.value--
        } else if (index == _currentIndex.value) {
            // 删除的是当前播放的歌曲
            _currentSong.value = queue.getOrNull(_currentIndex.value)
        }
    }

    /**
     * 清空队列
     */
    fun clearQueue() {
        _currentQueue.value = emptyList()
        _currentIndex.value = -1
        _currentSong.value = null
        shuffleHistory.clear()
        originalQueue = emptyList()
    }

    /**
     * 切换播放模式
     */
    fun setPlayMode(mode: PlayMode) {
        if (_playMode.value == mode) return

        val currentSong = _currentSong.value
        val currentIdx = _currentIndex.value

        _playMode.value = mode

        when (mode) {
            PlayMode.SEQUENTIAL -> {
                // 恢复原始顺序
                _currentQueue.value = originalQueue
                // 重新定位当前歌曲
                currentSong?.let { song ->
                    val newIndex = originalQueue.indexOfFirst { it.id == song.id }
                    if (newIndex >= 0) {
                        _currentIndex.value = newIndex
                    }
                }
            }
            PlayMode.SHUFFLE -> {
                // 保存原始顺序
                originalQueue = _currentQueue.value.toList()
                // 打乱队列，但保持当前歌曲在第一位
                val shuffled = _currentQueue.value.shuffled().toMutableList()
                currentSong?.let { song ->
                    shuffled.removeAll { it.id == song.id }
                    shuffled.add(0, song)
                }
                _currentQueue.value = shuffled
                _currentIndex.value = 0
            }
            PlayMode.REPEAT_ONE -> {
                // 单曲循环不改变队列
            }
        }

        shuffleHistory.clear()
        shuffleHistory.add(_currentIndex.value)
    }

    /**
     * 循环切换播放模式
     */
    fun togglePlayMode() {
        val nextMode = when (_playMode.value) {
            PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.REPEAT_ONE
            PlayMode.REPEAT_ONE -> PlayMode.SEQUENTIAL
        }
        setPlayMode(nextMode)
    }
}

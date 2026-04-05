package com.nekoplayer.data.model

/**
 * 统一歌曲模型
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val duration: Long, // 毫秒
    val source: MusicSource,
    val sourceId: String, // 原始平台ID
    val playUrl: String? = null,
    val lyric: String? = null,
    // 热度/质量评分（用于排序，越高越靠前）
    val popularityScore: Int = 0,
    // 是否标记为原唱/官方
    val isOfficial: Boolean = false
)

enum class MusicSource {
    BILIBILI,
    MIGU
}

/**
 * 播放状态
 */
enum class PlayerState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

/**
 * 播放器UI状态
 */
data class PlayerUiState(
    val currentSong: Song? = null,
    val state: PlayerState = PlayerState.IDLE,
    val position: Long = 0,
    val duration: Long = 0,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null
)

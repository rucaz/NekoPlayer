package com.nekoplayer.data.model

/**
 * 本地歌曲数据类
 */
data class LocalSong(
    val id: String,           // 唯一标识 (文件路径哈希)
    val uri: String,          // 文件URI
    val title: String,        // 歌曲标题
    val artist: String,       // 艺人
    val album: String?,       // 专辑
    val duration: Long,       // 时长 (毫秒)
    val coverPath: String?,   // 封面缓存路径
    val filePath: String,     // 文件路径
    val fileSize: Long,       // 文件大小
    val addedAt: Long,        // 添加时间
    val modifiedAt: Long      // 修改时间
) {
    /**
     * 转换为通用的 Song 对象
     */
    fun toSong(): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            coverUrl = coverPath ?: "",
            duration = duration,
            sourceId = uri,
            source = MusicSource.LOCAL,
            playUrl = uri
        )
    }
}

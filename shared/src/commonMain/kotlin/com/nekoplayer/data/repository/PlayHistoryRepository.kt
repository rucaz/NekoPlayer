package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 播放历史仓库
 */
class PlayHistoryRepository(
    private val database: NekoDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 记录播放历史
     * @param song 歌曲信息
     * @param playDuration 实际播放时长（毫秒）
     * @param songDuration 歌曲总时长（毫秒）
     * @param sessionId 播放会话ID（用于去重）
     */
    suspend fun recordPlay(
        song: Song,
        playDuration: Long,
        songDuration: Long,
        sessionId: String? = null
    ) {
        val progressPercent = if (songDuration > 0) {
            ((playDuration * 100) / songDuration).toInt()
        } else 0
        
        // 只记录播放超过30%的歌曲
        if (progressPercent < 30) return
        
        val songJson = json.encodeToString(Song.serializer(), song)
        
        database.playHistoryQueries.insertPlayRecord(
            songId = song.id,
            songJson = songJson,
            source = song.source.name,
            playDuration = playDuration,
            songDuration = songDuration,
            progressPercent = progressPercent,
            playedAt = System.currentTimeMillis(),
            playSessionId = sessionId
        )
    }
    
    /**
     * 获取最近播放列表（去重）
     * @param limit 返回数量限制
     */
    fun getRecentPlays(limit: Long = 500): Flow<List<Song>> {
        return database.playHistoryQueries
            .getRecentPlays(limit)
            .asFlow()
            .map { query ->
                query.executeAsList().mapNotNull { record ->
                    try {
                        json.decodeFromString(Song.serializer(), record.songJson)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
    }
    
    /**
     * 获取所有播放历史（不去重，用于统计）
     */
    fun getAllHistory(limit: Long = 1000, offset: Long = 0): Flow<List<PlayHistoryRecord>> {
        return database.playHistoryQueries
            .getAllHistory(limit, offset)
            .asFlow()
            .map { query ->
                query.executeAsList().map { record ->
                    PlayHistoryRecord(
                        id = record.id,
                        song = json.decodeFromString(Song.serializer(), record.songJson),
                        source = record.source,
                        playDuration = record.playDuration,
                        songDuration = record.songDuration,
                        progressPercent = record.progressPercent,
                        playedAt = record.playedAt
                    )
                }
            }
    }
    
    /**
     * 获取播放统计
     */
    suspend fun getPlayStats(limit: Long = 100): List<PlayCountStat> {
        return database.playHistoryQueries
            .getPlayCountStats(limit)
            .executeAsList()
            .map { stat ->
                PlayCountStat(
                    songId = stat.songId,
                    playCount = stat.playCount.toInt(),
                    totalPlayDuration = stat.totalPlayDuration ?: 0,
                    lastPlayedAt = stat.lastPlayedAt ?: 0
                )
            }
    }
    
    /**
     * 获取今日统计
     */
    suspend fun getTodayStats(): TodayStats {
        val startOfDay = getStartOfDayMillis()
        val result = database.playHistoryQueries
            .getTodayStats(startOfDay)
            .executeAsOne()
        
        return TodayStats(
            uniqueSongs = result.uniqueSongs?.toInt() ?: 0,
            totalPlays = result.totalPlays?.toInt() ?: 0,
            totalDuration = result.totalDuration ?: 0
        )
    }
    
    /**
     * 清空播放历史
     */
    suspend fun clearAll() {
        database.playHistoryQueries.clearAll()
    }
    
    /**
     * 删除单条记录
     */
    suspend fun deleteById(id: Long) {
        database.playHistoryQueries.deleteById(id)
    }
    
    /**
     * 删除N天前的记录
     */
    suspend fun deleteBefore(days: Int) {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000)
        database.playHistoryQueries.deleteBefore(cutoffTime)
    }
    
    /**
     * 获取记录总数
     */
    suspend fun getCount(): Long {
        return database.playHistoryQueries.getCount().executeAsOne()
    }
    
    private fun getStartOfDayMillis(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}

/**
 * 播放历史记录
 */
data class PlayHistoryRecord(
    val id: Long,
    val song: Song,
    val source: String,
    val playDuration: Long,
    val songDuration: Long,
    val progressPercent: Int,
    val playedAt: Long
)

/**
 * 播放次数统计
 */
data class PlayCountStat(
    val songId: String,
    val playCount: Int,
    val totalPlayDuration: Long,
    val lastPlayedAt: Long
)

/**
 * 今日统计
 */
data class TodayStats(
    val uniqueSongs: Int,
    val totalPlays: Int,
    val totalDuration: Long
)

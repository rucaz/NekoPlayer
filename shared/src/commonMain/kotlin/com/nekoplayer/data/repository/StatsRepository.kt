package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 播放统计仓库
 * 
 * 管理播放统计数据，支持：
 * - 按日统计聚合
 * - 按艺术家统计
 * - 按歌曲统计
 * - 时间分布分析（24小时）
 */
class StatsRepository(
    private val database: NekoDatabase
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 记录播放统计
     * @param song 歌曲
     * @param playDuration 播放时长
     * @param playedAt 播放时间戳
     */
    suspend fun recordPlay(
        song: Song,
        playDuration: Long,
        playedAt: Long = System.currentTimeMillis()
    ) {
        val date = formatDate(playedAt)
        val hour = getHour(playedAt)
        val songJson = json.encodeToString(Song.serializer(), song)
        
        // 1. 更新每日统计
        updateDailyStats(
            date = date,
            hour = hour,
            source = song.source,
            playDuration = playDuration,
            playedAt = playedAt
        )
        
        // 2. 更新艺术家统计
        updateArtistStats(
            artistName = song.artist,
            artistId = song.artistId,
            playDuration = playDuration,
            playedAt = playedAt
        )
        
        // 3. 更新歌曲统计
        updateSongStats(
            song = song,
            songJson = songJson,
            playDuration = playDuration,
            playedAt = playedAt
        )
    }
    
    /**
     * 获取每日统计
     */
    fun getDailyStats(date: String): Flow<DailyStats?> {
        return database.playStatsQueries
            .getDailyStats(date)
            .asFlow()
            .map { it.executeAsOneOrNull()?.toDailyStats() }
    }
    
    /**
     * 获取日期范围统计
     */
    fun getDailyStatsRange(startDate: String, endDate: String): Flow<List<DailyStats>> {
        return database.playStatsQueries
            .getDailyStatsRange(startDate, endDate)
            .asFlow()
            .map { query ->
                query.executeAsList().map { it.toDailyStats() }
            }
    }
    
    /**
     * 获取最近N天统计
     */
    fun getRecentDailyStats(days: Int): Flow<List<DailyStats>> {
        return database.playStatsQueries
            .getRecentDailyStats(days.toLong())
            .asFlow()
            .map { query ->
                query.executeAsList().map { it.toDailyStats() }
            }
    }
    
    /**
     * 获取Top艺术家（按播放次数）
     */
    fun getTopArtistsByPlays(limit: Int = 10): Flow<List<ArtistStats>> {
        return database.playStatsQueries
            .getTopArtistsByPlays(limit.toLong())
            .asFlow()
            .map { query ->
                query.executeAsList().map { it.toArtistStats() }
            }
    }
    
    /**
     * 获取Top艺术家（按播放时长）
     */
    fun getTopArtistsByDuration(limit: Int = 10): Flow<List<ArtistStats>> {
        return database.playStatsQueries
            .getTopArtistsByDuration(limit.toLong())
            .asFlow()
            .map { query ->
                query.executeAsList().map { it.toArtistStats() }
            }
    }
    
    /**
     * 获取Top歌曲
     */
    fun getTopSongs(limit: Int = 20): Flow<List<SongStats>> {
        return database.playStatsQueries
            .getTopSongs(limit.toLong())
            .asFlow()
            .map { query ->
                query.executeAsList().mapNotNull { 
                    try {
                        it.toSongStats(json)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
    }
    
    /**
     * 获取总体统计
     */
    fun getOverallStats(): Flow<OverallStats?> {
        return database.playStatsQueries
            .getOverallStats()
            .asFlow()
            .map { it.executeAsOneOrNull()?.toOverallStats() }
    }
    
    /**
     * 获取本周统计
     */
    fun getThisWeekStats(): Flow<WeekMonthStats?> {
        return database.playStatsQueries
            .getThisWeekStats()
            .asFlow()
            .map { it.executeAsOneOrNull()?.toWeekMonthStats() }
    }
    
    /**
     * 获取本月统计
     */
    fun getThisMonthStats(): Flow<WeekMonthStats?> {
        return database.playStatsQueries
            .getThisMonthStats()
            .asFlow()
            .map { it.executeAsOneOrNull()?.toWeekMonthStats() }
    }
    
    /**
     * 获取某歌曲统计
     */
    fun getSongStats(songId: String): Flow<SongStats?> {
        return database.playStatsQueries
            .getSongStats(songId)
            .asFlow()
            .map { query ->
                query.executeAsOneOrNull()?.let {
                    try {
                        it.toSongStats(json)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
    }
    
    /**
     * 获取24小时时间分布
     */
    suspend fun getHourlyDistribution(days: Int = 30): List<Int> {
        val stats = database.playStatsQueries
            .getRecentDailyStats(days.toLong())
            .executeAsList()
        
        // 聚合24小时数据
        val hourly = MutableList(24) { 0 }
        stats.forEach { daily ->
            hourly[0] += daily.hour00.toInt()
            hourly[1] += daily.hour01.toInt()
            hourly[2] += daily.hour02.toInt()
            hourly[3] += daily.hour03.toInt()
            hourly[4] += daily.hour04.toInt()
            hourly[5] += daily.hour05.toInt()
            hourly[6] += daily.hour06.toInt()
            hourly[7] += daily.hour07.toInt()
            hourly[8] += daily.hour08.toInt()
            hourly[9] += daily.hour09.toInt()
            hourly[10] += daily.hour10.toInt()
            hourly[11] += daily.hour11.toInt()
            hourly[12] += daily.hour12.toInt()
            hourly[13] += daily.hour13.toInt()
            hourly[14] += daily.hour14.toInt()
            hourly[15] += daily.hour15.toInt()
            hourly[16] += daily.hour16.toInt()
            hourly[17] += daily.hour17.toInt()
            hourly[18] += daily.hour18.toInt()
            hourly[19] += daily.hour19.toInt()
            hourly[20] += daily.hour20.toInt()
            hourly[21] += daily.hour21.toInt()
            hourly[22] += daily.hour22.toInt()
            hourly[23] += daily.hour23.toInt()
        }
        return hourly
    }
    
    // ==================== 私有方法 ====================
    
    private suspend fun updateDailyStats(
        date: String,
        hour: Int,
        source: com.nekoplayer.data.model.MusicSource,
        playDuration: Long,
        playedAt: Long
    ) {
        val hourValues = MutableList(24) { 0 }
        hourValues[hour] = 1
        
        val (bilibili, migu, local) = when (source) {
            com.nekoplayer.data.model.MusicSource.BILIBILI -> Triple(1, 0, 0)
            com.nekoplayer.data.model.MusicSource.MIGU -> Triple(0, 1, 0)
            com.nekoplayer.data.model.MusicSource.LOCAL -> Triple(0, 0, 1)
            else -> Triple(0, 0, 0)
        }
        
        database.playStatsQueries.upsertDailyStats(
            date = date,
            totalPlays = 1,
            uniqueSongs = 1,  // 简化处理，实际需要计算
            totalDuration = playDuration,
            bilibiliPlays = bilibli.toLong(),
            miguPlays = migu.toLong(),
            localPlays = local.toLong(),
            hour00 = hourValues[0].toLong(), hour01 = hourValues[1].toLong(),
            hour02 = hourValues[2].toLong(), hour03 = hourValues[3].toLong(),
            hour04 = hourValues[4].toLong(), hour05 = hourValues[5].toLong(),
            hour06 = hourValues[6].toLong(), hour07 = hourValues[7].toLong(),
            hour08 = hourValues[8].toLong(), hour09 = hourValues[9].toLong(),
            hour10 = hourValues[10].toLong(), hour11 = hourValues[11].toLong(),
            hour12 = hourValues[12].toLong(), hour13 = hourValues[13].toLong(),
            hour14 = hourValues[14].toLong(), hour15 = hourValues[15].toLong(),
            hour16 = hourValues[16].toLong(), hour17 = hourValues[17].toLong(),
            hour18 = hourValues[18].toLong(), hour19 = hourValues[19].toLong(),
            hour20 = hourValues[20].toLong(), hour21 = hourValues[21].toLong(),
            hour22 = hourValues[22].toLong(), hour23 = hourValues[23].toLong(),
            updatedAt = playedAt
        )
    }
    
    private suspend fun updateArtistStats(
        artistName: String,
        artistId: String?,
        playDuration: Long,
        playedAt: Long
    ) {
        val existing = database.playStatsQueries
            .getArtistStats(artistName)
            .executeAsOneOrNull()
        
        database.playStatsQueries.upsertArtistStats(
            artistName = artistName,
            artistId = artistId,
            totalPlays = 1,
            totalDuration = playDuration,
            uniqueSongs = 1,
            firstPlayedAt = existing?.firstPlayedAt ?: playedAt,
            lastPlayedAt = playedAt,
            updatedAt = playedAt
        )
    }
    
    private suspend fun updateSongStats(
        song: Song,
        songJson: String,
        playDuration: Long,
        playedAt: Long
    ) {
        val existing = database.playStatsQueries
            .getSongStats(song.id)
            .executeAsOneOrNull()
        
        database.playStatsQueries.upsertSongStats(
            songId = song.id,
            songJson = songJson,
            totalPlays = 1,
            totalDuration = playDuration,
            firstPlayedAt = existing?.firstPlayedAt ?: playedAt,
            lastPlayedAt = playedAt,
            updatedAt = playedAt
        )
    }
    
    private fun formatDate(timestamp: Long): String {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
        val localDate = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        return "${localDate.year}-${localDate.monthNumber.toString().padStart(2, '0')}-${localDate.dayOfMonth.toString().padStart(2, '0')}"
    }
    
    private fun getHour(timestamp: Long): Int {
        val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
        return instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).hour
    }
    
    // ==================== 数据模型转换 ====================
    
    private fun com.nekoplayer.database.PlayStatsDaily.toDailyStats() = DailyStats(
        date = date,
        totalPlays = totalPlays.toInt(),
        uniqueSongs = uniqueSongs.toInt(),
        totalDuration = totalDuration,
        sourceBreakdown = SourceBreakdown(
            bilibili = bilibiliPlays.toInt(),
            migu = miguPlays.toInt(),
            local = localPlays.toInt()
        ),
        hourlyDistribution = listOf(
            hour00.toInt(), hour01.toInt(), hour02.toInt(), hour03.toInt(),
            hour04.toInt(), hour05.toInt(), hour06.toInt(), hour07.toInt(),
            hour08.toInt(), hour09.toInt(), hour10.toInt(), hour11.toInt(),
            hour12.toInt(), hour13.toInt(), hour14.toInt(), hour15.toInt(),
            hour16.toInt(), hour17.toInt(), hour18.toInt(), hour19.toInt(),
            hour20.toInt(), hour21.toInt(), hour22.toInt(), hour23.toInt()
        )
    )
    
    private fun com.nekoplayer.database.PlayStatsArtist.toArtistStats() = ArtistStats(
        artistName = artistName,
        artistId = artistId,
        totalPlays = totalPlays.toInt(),
        totalDuration = totalDuration,
        uniqueSongs = uniqueSongs.toInt(),
        firstPlayedAt = firstPlayedAt,
        lastPlayedAt = lastPlayedAt
    )
    
    private fun com.nekoplayer.database.PlayStatsSong.toSongStats(json: Json) = SongStats(
        song = json.decodeFromString(Song.serializer(), songJson),
        totalPlays = totalPlays.toInt(),
        totalDuration = totalDuration,
        firstPlayedAt = firstPlayedAt,
        lastPlayedAt = lastPlayedAt
    )
    
    private fun com.nekoplayer.database.GetOverallStats.toOverallStats() = OverallStats(
        totalPlays = totalPlays?.toInt() ?: 0,
        totalDuration = totalDuration ?: 0,
        activeDays = activeDays?.toInt() ?: 0
    )
    
    private fun com.nekoplayer.database.GetThisWeekStats.toWeekMonthStats() = WeekMonthStats(
        totalPlays = totalPlays?.toInt() ?: 0,
        uniqueSongs = uniqueSongs?.toInt() ?: 0,
        totalDuration = totalDuration ?: 0
    )
}

// ==================== 数据模型 ====================

data class DailyStats(
    val date: String,
    val totalPlays: Int,
    val uniqueSongs: Int,
    val totalDuration: Long,
    val sourceBreakdown: SourceBreakdown,
    val hourlyDistribution: List<Int>
)

data class SourceBreakdown(
    val bilibili: Int,
    val migu: Int,
    val local: Int
)

data class ArtistStats(
    val artistName: String,
    val artistId: String?,
    val totalPlays: Int,
    val totalDuration: Long,
    val uniqueSongs: Int,
    val firstPlayedAt: Long?,
    val lastPlayedAt: Long?
)

data class SongStats(
    val song: Song,
    val totalPlays: Int,
    val totalDuration: Long,
    val firstPlayedAt: Long?,
    val lastPlayedAt: Long?
)

data class OverallStats(
    val totalPlays: Int,
    val totalDuration: Long,
    val activeDays: Int
)

data class WeekMonthStats(
    val totalPlays: Int,
    val uniqueSongs: Int,
    val totalDuration: Long
)

package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import com.nekoplayer.database.Playlist
import com.nekoplayer.database.PlaylistSong
import com.nekoplayer.utils.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 歌单数据仓库
 */
class PlaylistRepository(private val database: NekoDatabase) {

    private val json = Json { ignoreUnknownKeys = true }

    // ==================== 歌单操作 ====================

    /**
     * 获取所有歌单
     */
    fun getAllPlaylists(): Flow<List<Playlist>> = flow {
        emit(database.playlistQueries.getAll().executeAsList())
    }.flowOn(Dispatchers.Default)

    /**
     * 根据ID获取歌单
     */
    suspend fun getPlaylistById(id: String): Playlist? = withContext(Dispatchers.Default) {
        database.playlistQueries.getById(id).executeAsOneOrNull()
    }

    /**
     * 创建歌单
     */
    suspend fun createPlaylist(name: String, coverUrl: String? = null): String = withContext(Dispatchers.Default) {
        val id = generateId()
        val now = getCurrentTimeMillis()
        database.playlistQueries.insert(
            id = id,
            name = name,
            coverUrl = coverUrl,
            createdAt = now,
            updatedAt = now
        )
        id
    }

    /**
     * 更新歌单信息
     */
    suspend fun updatePlaylist(id: String, name: String, coverUrl: String? = null) = withContext(Dispatchers.Default) {
        database.playlistQueries.update(
            name = name,
            coverUrl = coverUrl,
            updatedAt = getCurrentTimeMillis(),
            id = id
        )
    }

    /**
     * 更新歌单封面
     */
    suspend fun updatePlaylistCover(id: String, coverUrl: String?) = withContext(Dispatchers.Default) {
        database.playlistQueries.updateCover(
            coverUrl = coverUrl,
            updatedAt = getCurrentTimeMillis(),
            id = id
        )
    }

    /**
     * 删除歌单（关联的歌曲会自动删除，因为有外键约束）
     */
    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.Default) {
        database.playlistQueries.delete(id)
    }

    // ==================== 歌单歌曲操作 ====================

    /**
     * 获取歌单内所有歌曲
     */
    fun getSongsInPlaylist(playlistId: String): Flow<List<Pair<PlaylistSong, Song>>> = flow {
        val songs = database.playlistSongQueries.getSongsByPlaylist(playlistId).executeAsList()
            .map { playlistSong ->
                val song = json.decodeFromString<Song>(playlistSong.songJson)
                playlistSong to song
            }
        emit(songs)
    }.flowOn(Dispatchers.Default)

    /**
     * 获取歌单内歌曲数量
     */
    suspend fun getSongCountInPlaylist(playlistId: String): Long = withContext(Dispatchers.Default) {
        database.playlistSongQueries.getSongCountByPlaylist(playlistId).executeAsOne()
    }

    /**
     * 检查歌曲是否已在歌单中
     */
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val count = database.playlistSongQueries.isSongInPlaylist(playlistId, songId).executeAsOne()
            count > 0
        } catch (e: Exception) {
            // 查询失败时默认返回false，允许尝试添加
            false
        }
    }

    /**
     * 添加歌曲到歌单
     * @return 是否成功添加（如果歌曲已存在则返回false）
     */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): Boolean = withContext(Dispatchers.Default) {
        println("[NekoPlaylist] addSongToPlaylist called: playlistId=$playlistId, songId=${song.id}")
        try {
            // 检查是否已存在
            println("[NekoPlaylist] Checking if song exists...")
            val count = database.playlistSongQueries.isSongInPlaylist(playlistId, song.id).executeAsOne()
            println("[NekoPlaylist] Count result: $count")
            val exists = count > 0
            
            if (exists) {
                println("[NekoPlaylist] Song already exists, returning true (idempotent)")
                return@withContext true
            }

            // 获取当前最大排序值
            println("[NekoPlaylist] Getting max order...")
            val maxOrderResult = database.playlistSongQueries.getMaxOrder(playlistId).executeAsOneOrNull()
            val maxOrder = maxOrderResult?.toString()?.toLongOrNull() ?: 0L
            val nextOrder = maxOrder + 1L
            println("[NekoPlaylist] Next order: $nextOrder")

            // 插入歌曲
            println("[NekoPlaylist] Inserting song...")
            val songJson = json.encodeToString(song)
            database.playlistSongQueries.insert(
                id = generateId(),
                playlistId = playlistId,
                songId = song.id,
                songJson = songJson,
                addedAt = getCurrentTimeMillis(),
                order = nextOrder
            )
            println("[NekoPlaylist] Insert successful")

            // 更新歌单更新时间
            try {
                println("[NekoPlaylist] Updating playlist timestamp...")
                val playlist = database.playlistQueries.getById(playlistId).executeAsOneOrNull()
                if (playlist != null) {
                    database.playlistQueries.update(
                        name = playlist.name,
                        coverUrl = playlist.coverUrl,
                        updatedAt = getCurrentTimeMillis(),
                        id = playlistId
                    )
                    println("[NekoPlaylist] Playlist timestamp updated")
                }
            } catch (e: Exception) {
                println("[NekoPlaylist] WARNING: Failed to update playlist timestamp: ${e.message}")
                e.printStackTrace()
            }

            println("[NekoPlaylist] Returning true")
            true
        } catch (e: Exception) {
            println("[NekoPlaylist] ERROR adding song: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * 从歌单删除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistSongId: String) = withContext(Dispatchers.Default) {
        database.playlistSongQueries.delete(playlistSongId)
    }

    /**
     * 根据歌单ID和歌曲ID删除
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.Default) {
        database.playlistSongQueries.deleteByPlaylistAndSong(playlistId, songId)
    }

    /**
     * 清空歌单所有歌曲
     */
    suspend fun clearPlaylist(playlistId: String) = withContext(Dispatchers.Default) {
        database.playlistSongQueries.deleteAllByPlaylist(playlistId)
    }

    // ==================== 工具方法 ====================

    private fun generateId(): String {
        // 使用 Kotlin 跨平台兼容的方式生成 ID
        return "${getCurrentTimeMillis()}_${randomString(8)}"
    }

    private fun getCurrentTimeMillis(): Long {
        return currentTimeMillis()
    }

    private fun randomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}
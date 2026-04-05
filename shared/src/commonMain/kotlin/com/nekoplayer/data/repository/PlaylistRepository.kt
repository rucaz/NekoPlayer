package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import com.nekoplayer.database.Playlist
import com.nekoplayer.database.PlaylistSong
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
    }.flowOn(Dispatchers.IO)

    /**
     * 根据ID获取歌单
     */
    suspend fun getPlaylistById(id: String): Playlist? = withContext(Dispatchers.IO) {
        database.playlistQueries.getById(id).executeAsOneOrNull()
    }

    /**
     * 创建歌单
     */
    suspend fun createPlaylist(name: String, coverUrl: String? = null): String = withContext(Dispatchers.IO) {
        val id = generateId()
        val now = System.currentTimeMillis()
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
    suspend fun updatePlaylist(id: String, name: String, coverUrl: String? = null) = withContext(Dispatchers.IO) {
        database.playlistQueries.update(
            name = name,
            coverUrl = coverUrl,
            updatedAt = System.currentTimeMillis(),
            id = id
        )
    }

    /**
     * 更新歌单封面
     */
    suspend fun updatePlaylistCover(id: String, coverUrl: String?) = withContext(Dispatchers.IO) {
        database.playlistQueries.updateCover(
            coverUrl = coverUrl,
            updatedAt = System.currentTimeMillis(),
            id = id
        )
    }

    /**
     * 删除歌单（关联的歌曲会自动删除，因为有外键约束）
     */
    suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) {
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
    }.flowOn(Dispatchers.IO)

    /**
     * 获取歌单内歌曲数量
     */
    suspend fun getSongCountInPlaylist(playlistId: String): Long = withContext(Dispatchers.IO) {
        database.playlistSongQueries.getSongCountByPlaylist(playlistId).executeAsOne()
    }

    /**
     * 检查歌曲是否已在歌单中
     */
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean = withContext(Dispatchers.IO) {
        database.playlistSongQueries.isSongInPlaylist(playlistId, songId).executeAsOne() > 0
    }

    /**
     * 添加歌曲到歌单
     * @return 是否成功添加（如果歌曲已存在则返回false）
     */
    suspend fun addSongToPlaylist(playlistId: String, song: Song): Boolean = withContext(Dispatchers.IO) {
        // 检查是否已存在
        if (isSongInPlaylist(playlistId, song.id)) {
            return@withContext false
        }

        // 获取当前最大排序值
        val songs = database.playlistSongQueries.getSongsByPlaylist(playlistId).executeAsList()
        val maxOrder = songs.maxOfOrNull { it.order } ?: 0L
        val nextOrder = maxOrder + 1

        database.playlistSongQueries.insert(
            id = generateId(),
            playlistId = playlistId,
            songId = song.id,
            songJson = json.encodeToString(song),
            addedAt = System.currentTimeMillis(),
            order = nextOrder
        )

        // 更新歌单更新时间
        database.playlistQueries.update(
            name = database.playlistQueries.getById(playlistId).executeAsOne().name,
            coverUrl = database.playlistQueries.getById(playlistId).executeAsOne().coverUrl,
            updatedAt = System.currentTimeMillis(),
            id = playlistId
        )

        true
    }

    /**
     * 从歌单删除歌曲
     */
    suspend fun removeSongFromPlaylist(playlistSongId: String) = withContext(Dispatchers.IO) {
        database.playlistSongQueries.delete(playlistSongId)
    }

    /**
     * 根据歌单ID和歌曲ID删除
     */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String) = withContext(Dispatchers.IO) {
        database.playlistSongQueries.deleteByPlaylistAndSong(playlistId, songId)
    }

    /**
     * 清空歌单所有歌曲
     */
    suspend fun clearPlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        database.playlistSongQueries.deleteAllByPlaylist(playlistId)
    }

    // ==================== 工具方法 ====================

    private fun generateId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}

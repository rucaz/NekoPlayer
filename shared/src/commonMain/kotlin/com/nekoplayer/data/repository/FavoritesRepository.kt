package com.nekoplayer.data.repository

import com.nekoplayer.data.model.Song
import com.nekoplayer.database.NekoDatabase
import com.nekoplayer.utils.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 收藏/爱听歌单管理
 * 自动创建和维护"我喜欢的音乐"歌单
 */
class FavoritesRepository(private val database: NekoDatabase) {

    companion object {
        const val FAVORITES_PLAYLIST_NAME = "我喜欢的音乐"
        const val FAVORITES_PLAYLIST_ID_PREFIX = "favorites_"
    }

    private var favoritesPlaylistId: String? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 获取或创建收藏歌单ID
     */
    suspend fun getOrCreateFavoritesPlaylistId(): String = withContext(Dispatchers.Default) {
        favoritesPlaylistId?.let { return@withContext it }

        // 查找是否已有收藏歌单
        val existing = database.playlistQueries.getAll().executeAsList()
            .find { it.name == FAVORITES_PLAYLIST_NAME }

        if (existing != null) {
            favoritesPlaylistId = existing.id
            return@withContext existing.id
        }

        // 创建新的收藏歌单
        val id = "${FAVORITES_PLAYLIST_ID_PREFIX}${currentTimeMillis()}_${randomString(8)}"
        val now = currentTimeMillis()
        database.playlistQueries.insert(
            id = id,
            name = FAVORITES_PLAYLIST_NAME,
            coverUrl = null,
            createdAt = now,
            updatedAt = now
        )
        favoritesPlaylistId = id
        id
    }

    /**
     * 检查歌曲是否已收藏
     */
    suspend fun isFavorite(songId: String): Boolean = withContext(Dispatchers.Default) {
        val playlistId = getOrCreateFavoritesPlaylistId()
        database.playlistSongQueries.isSongInPlaylist(playlistId, songId).executeAsOne() > 0
    }

    /**
     * 添加/取消收藏
     * @return 操作后的收藏状态（true=已收藏, false=未收藏）
     */
    suspend fun toggleFavorite(song: Song): Boolean = withContext(Dispatchers.Default) {
        val playlistId = getOrCreateFavoritesPlaylistId()
        val exists = database.playlistSongQueries.isSongInPlaylist(playlistId, song.id).executeAsOne() > 0

        if (exists) {
            // 取消收藏
            database.playlistSongQueries.deleteByPlaylistAndSong(playlistId, song.id)
            false
        } else {
            // 添加收藏
            val maxOrderResult = database.playlistSongQueries.getMaxOrder(playlistId).executeAsOneOrNull()
            val maxOrder = maxOrderResult?.toString()?.toLongOrNull() ?: 0L
            database.playlistSongQueries.insert(
                id = generateId(),
                playlistId = playlistId,
                songId = song.id,
                songJson = json.encodeToString(song),
                addedAt = currentTimeMillis(),
                order = maxOrder + 1L
            )
            // 更新歌单时间
            val playlist = database.playlistQueries.getById(playlistId).executeAsOne()
            database.playlistQueries.update(
                name = playlist.name,
                coverUrl = playlist.coverUrl,
                updatedAt = currentTimeMillis(),
                id = playlistId
            )
            true
        }
    }

    /**
     * 添加收藏（如果未收藏）
     */
    suspend fun addToFavorites(song: Song): Boolean = withContext(Dispatchers.Default) {
        if (isFavorite(song.id)) return@withContext false
        toggleFavorite(song)
        true
    }

    /**
     * 获取收藏歌单ID（供外部使用）
     */
    suspend fun getFavoritesPlaylistId(): String {
        return getOrCreateFavoritesPlaylistId()
    }

    /**
     * 获取收藏数量
     */
    fun getFavoritesCountFlow(): Flow<Long> = flow {
        val playlistId = getOrCreateFavoritesPlaylistId()
        emit(database.playlistSongQueries.getSongCountByPlaylist(playlistId).executeAsOne())
    }.flowOn(Dispatchers.Default)

    private fun generateId(): String {
        return "${currentTimeMillis()}_${randomString(8)}"
    }

    private fun randomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }
}
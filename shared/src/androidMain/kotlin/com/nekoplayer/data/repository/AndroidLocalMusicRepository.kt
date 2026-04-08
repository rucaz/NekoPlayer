package com.nekoplayer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.nekoplayer.data.model.LocalSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Android 平台本地音乐仓库实现
 */
class AndroidLocalMusicRepository(
    private val context: Context
) : LocalMusicRepository {
    
    private val _localSongs = MutableStateFlow<List<LocalSong>>(emptyList())
    override fun getLocalSongs(): Flow<List<LocalSong>> = _localSongs.asStateFlow()
    
    override fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    
    override suspend fun requestPermission(): Boolean {
        // 权限请求需要在 Activity 中进行，这里返回当前权限状态
        // 实际权限请求由 UI 层处理
        return hasPermission()
    }
    
    override suspend fun scanMusic(): List<LocalSong> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext emptyList()
        }
        
        val songs = mutableListOf<LocalSong>()
        val contentResolver: ContentResolver = context.contentResolver
        
        // 查询的列
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.IS_MUSIC
        )
        
        // 选择条件：是音乐文件且时长大于10秒
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        
        // 排序：按添加时间倒序
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val filePath = cursor.getString(dataColumn) ?: continue
                val addedAt = cursor.getLong(addedColumn) * 1000 // 转换为毫秒
                val modifiedAt = cursor.getLong(modifiedColumn) * 1000
                
                // 生成内容URI
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                ).toString()
                
                // 生成唯一ID (文件路径的MD5)
                val uniqueId = generateId(filePath)
                
                songs.add(
                    LocalSong(
                        id = uniqueId,
                        uri = contentUri,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        coverPath = null, // 后续从ID3标签读取
                        filePath = filePath,
                        fileSize = size,
                        addedAt = addedAt,
                        modifiedAt = modifiedAt
                    )
                )
            }
        }
        
        _localSongs.value = songs
        songs
    }
    
    override suspend fun refresh() {
        scanMusic()
    }
    
    /**
     * 从文件路径生成唯一ID
     */
    private fun generateId(filePath: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(filePath.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 读取内嵌封面 (扩展功能)
     */
    suspend fun extractEmbeddedCover(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            // 使用媒体元数据检索器读取封面
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val art = retriever.embeddedPicture
            retriever.release()
            
            if (art != null) {
                // 保存到缓存目录
                val cacheDir = File(context.cacheDir, "album_art")
                cacheDir.mkdirs()
                val coverFile = File(cacheDir, "${generateId(filePath)}.jpg")
                coverFile.writeBytes(art)
                return@withContext coverFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}

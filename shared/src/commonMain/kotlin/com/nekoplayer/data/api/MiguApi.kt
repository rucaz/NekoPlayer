package com.nekoplayer.data.api

import com.nekoplayer.data.model.Song
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 咪咕音乐API（免登录）
 */
class MiguApi(engine: HttpClientEngine) {
    
    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Linux; Android 10)")
            header("Referer", "https://m.music.migu.cn/")
        }
    }
    
    /**
     * 搜索音乐
     */
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): List<Song> {
        val response = client.get("https://m.music.migu.cn/miguus/remoting/scr_search_tag") {
            parameter("keyword", keyword)
            parameter("type", "2") // 歌曲
            parameter("pageNo", page)
            parameter("pageSize", pageSize)
        }
        
        val result = response.body<MiguSearchResponse>()
        return result.musics?.map { music ->
            Song(
                id = "migu_${music.id}",
                title = music.title,
                artist = music.singerName,
                album = music.albumName,
                coverUrl = music.cover,
                duration = 0, // 咪咕API不返回时长，需要单独获取
                source = com.nekoplayer.data.model.MusicSource.MIGU,
                sourceId = music.copyrightId,
                playUrl = null
            )
        } ?: emptyList()
    }
    
    /**
     * 获取播放链接
     * 音质：1-标准(128k) 2-高清(320k) 3-无损(flac)
     */
    suspend fun getPlayUrl(copyrightId: String, quality: Int = 2): String? {
        // 咪咕播放链接需要特殊处理，这里使用公开接口
        // 实际生产环境可能需要自建解析服务
        val response = client.get("https://app.pd.nf.migu.cn/MIGU/1802210000000091022/v2.0") {
            parameter("copyrightId", copyrightId)
            parameter("resourceType", "2")
            parameter("purpose", "1")
            parameter("quality", quality.toString())
        }
        
        // 咪咕返回的是XML或特殊格式，需要解析
        // 这里简化处理，实际实现需要完整解析逻辑
        return null
    }
}

// Migu API响应数据类
@Serializable
data class MiguSearchResponse(
    val musics: List<MiguMusic>? = null
)

@Serializable
data class MiguMusic(
    val id: String = "",
    val title: String = "",
    val singerName: String = "",
    val albumName: String = "",
    val copyrightId: String = "",
    val cover: String = ""
)

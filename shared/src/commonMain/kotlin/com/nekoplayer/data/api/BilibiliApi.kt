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
 * Bilibili音频API
 */
class BilibiliApi(engine: HttpClientEngine) {
    
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
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            header("Referer", "https://search.bilibili.com")
        }
    }
    
    /**
     * 搜索音乐
     */
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): List<Song> {
        val response = client.get("https://api.bilibili.com/x/web-interface/search/type") {
            parameter("keyword", keyword)
            parameter("search_type", "video")
            parameter("page", page)
            parameter("page_size", pageSize)
        }
        
        val result = response.body<BiliSearchResponse>()
        return result.data?.result?.map { video ->
            Song(
                id = "bili_${video.bvid}",
                title = video.title.replace("<em class=\"keyword\">", "").replace("</em>", ""),
                artist = video.author,
                album = "",
                coverUrl = "https:${video.pic}",
                duration = video.duration.toLong() * 1000,
                source = com.nekoplayer.data.model.MusicSource.BILIBILI,
                sourceId = video.bvid,
                playUrl = null // 需要单独获取
            )
        } ?: emptyList()
    }
    
    /**
     * 获取视频播放链接
     */
    suspend fun getPlayUrl(bvid: String, cid: Long? = null): String? {
        val actualCid = cid ?: getCid(bvid)
        
        val response = client.get("https://api.bilibili.com/x/player/playurl") {
            parameter("bvid", bvid)
            parameter("cid", actualCid)
            parameter("qn", "112") // 1080P+
            parameter("fnval", "16") // DASH格式
            parameter("fourk", "1")
        }
        
        val result = response.body<BiliPlayUrlResponse>()
        return result.data?.dash?.audio?.firstOrNull()?.baseUrl
            ?: result.data?.durl?.firstOrNull()?.url
    }
    
    /**
     * 获取视频CID（必需参数）
     */
    private suspend fun getCid(bvid: String): Long {
        val response = client.get("https://api.bilibili.com/x/web-interface/view") {
            parameter("bvid", bvid)
        }
        val result = response.body<BiliVideoInfoResponse>()
        return result.data?.cid ?: throw Exception("Failed to get CID")
    }
}

// Bilibili API响应数据类
@Serializable
data class BiliSearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BiliSearchData? = null
)

@Serializable
data class BiliSearchData(
    val result: List<BiliVideoItem> = emptyList()
)

@Serializable
data class BiliVideoItem(
    val bvid: String,
    val title: String,
    val author: String,
    val pic: String,
    val duration: String
)

@Serializable
data class BiliVideoInfoResponse(
    val code: Int = 0,
    val data: BiliVideoInfo? = null
)

@Serializable
data class BiliVideoInfo(
    val cid: Long = 0
)

@Serializable
data class BiliPlayUrlResponse(
    val code: Int = 0,
    val data: BiliPlayData? = null
)

@Serializable
data class BiliPlayData(
    val dash: BiliDash? = null,
    val durl: List<BiliDurl>? = null
)

@Serializable
data class BiliDash(
    val audio: List<BiliAudio>? = null
)

@Serializable
data class BiliAudio(
    val baseUrl: String = "",
    val backupUrl: List<String> = emptyList()
)

@Serializable
data class BiliDurl(
    val url: String = ""
)

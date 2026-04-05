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
     * 搜索音乐（优化排序，优先原唱/官方）
     */
    suspend fun search(keyword: String, page: Int = 1, pageSize: Int = 20): List<Song> {
        // 使用收藏数排序，更可能优先高质量原唱
        val response = client.get("https://api.bilibili.com/x/web-interface/search/type") {
            parameter("keyword", keyword)
            parameter("search_type", "video")
            parameter("page", page)
            parameter("page_size", pageSize)
            parameter("highlight", "1")
            // 按收藏数排序，更容易找到原唱/高质量内容
            parameter("order", "stow")
        }
        
        val result = response.body<BiliSearchResponse>()
        return result.data?.result?.map { video ->
            // 计算热度评分 (收藏*3 + 弹幕*2 + 播放/100)
            val popularityScore = (video.favorites * 3) + 
                                  (video.danmaku * 2) + 
                                  (video.play / 100).toInt()
            
            Song(
                id = "bili_${video.bvid}",
                title = video.title.replace("<em class=\"keyword\">", "").replace("</em>", ""),
                artist = video.author,
                album = "",
                coverUrl = "https:${video.pic}",
                duration = parseDurationToMillis(video.duration),
                source = com.nekoplayer.data.model.MusicSource.BILIBILI,
                sourceId = video.bvid,
                playUrl = null,
                popularityScore = popularityScore,
                isOfficial = isOfficialContent(video.title)
            )
        } ?: emptyList()
    }
    
    /**
     * 模糊搜索 - 智能排序
     */
    suspend fun fuzzySearch(keyword: String): List<Song> {
        // 主搜索（按收藏数）
        val mainResults = search(keyword)
        
        // 补充搜索：尝试原始关键词变体
        val allResults = if (mainResults.size < 10 && keyword.length > 2) {
            val cleanedKeyword = keyword.replace(Regex("[^\u4e00-\u9fa5a-zA-Z0-9]"), " ")
            if (cleanedKeyword != keyword && cleanedKeyword.isNotBlank()) {
                mainResults + search(cleanedKeyword)
            } else mainResults
        } else mainResults
        
        // 去重并智能排序
        return allResults
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Song> { song ->
                // 1. 标题匹配度（最高优先级）
                calculateRelevance(song.title, keyword)
            }.thenByDescending { song ->
                // 2. 官方/原唱标记
                if (song.isOfficial) 1000 else 0
            }.thenByDescending { song ->
                // 3. 排除翻唱/低质量内容
                if (isLowQualityContent(song.title)) -500 else 0
            }.thenByDescending { song ->
                // 4. 热度评分
                song.popularityScore
            })
    }
    
    /**
     * 判断是否为官方/原唱内容
     */
    private fun isOfficialContent(title: String): Boolean {
        val lowerTitle = title.lowercase()
        val officialKeywords = listOf(
            "原唱", "原版", "官方", "official", "mv", "music video",
            "完整版", "full version", "本家", "原创"
        )
        return officialKeywords.any { lowerTitle.contains(it) }
    }
    
    /**
     * 判断是否为低质量/翻唱内容（需要降级）
     */
    private fun isLowQualityContent(title: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowQualityKeywords = listOf(
            "翻唱", "cover", "二创", "手书", "mmd", "鬼畜", "搞笑",
            " reaction", "反应", "吐槽", "解说", "剪辑", "盘点"
        )
        return lowQualityKeywords.any { lowerTitle.contains(it) }
    }
    
    /**
     * 计算搜索结果与关键词的相关性分数
     */
    private fun calculateRelevance(title: String, keyword: String): Int {
        val lowerTitle = title.lowercase()
        val lowerKeyword = keyword.lowercase()
        
        return when {
            // 完全匹配分数最高
            lowerTitle == lowerKeyword -> 100
            // 开头匹配
            lowerTitle.startsWith(lowerKeyword) -> 80
            // 包含完整关键词
            lowerTitle.contains(lowerKeyword) -> 60
            // 包含关键词的每个字/词（模糊匹配）
            lowerKeyword.split("").all { it.isBlank() || lowerTitle.contains(it) } -> 40
            // 部分匹配
            else -> lowerKeyword.split(" ").count { lowerTitle.contains(it) } * 20
        }
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

/**
 * 解析B站时长字符串为毫秒
 * 格式: "3:35" (分:秒) 或 "120" (纯秒数)
 */
private fun parseDurationToMillis(duration: String): Long {
    return try {
        when {
            // 格式: "3:35" 或 "1:23:45"
            duration.contains(":") -> {
                val parts = duration.split(":")
                val seconds = when (parts.size) {
                    2 -> parts[0].toInt() * 60 + parts[1].toInt()  // MM:SS
                    3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()  // HH:MM:SS
                    else -> 0
                }
                seconds * 1000L
            }
            // 格式: 纯数字秒数
            else -> duration.toInt() * 1000L
        }
    } catch (e: Exception) {
        0L // 解析失败返回0
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
    val duration: String,
    val favorites: Int = 0,    // 收藏数
    val danmaku: Int = 0,      // 弹幕数
    val play: Int = 0          // 播放量
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

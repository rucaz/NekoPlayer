package com.nekoplayer.data.api

import com.nekoplayer.utils.currentTimeMillis
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Bilibili登录API
 * 实现扫码登录流程
 */
class BiliLoginApi(engine: HttpClientEngine) {
    
    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            })
        }
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 15000
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            header("Referer", "https://passport.bilibili.com")
            header("Accept", "application/json, text/plain, */*")
            header("Accept-Language", "zh-CN,zh;q=0.9")
        }
    }
    
    /**
     * 获取登录二维码
     */
    suspend fun getLoginQrCode(): QrCodeResult {
        return try {
            val timestamp = currentTimeMillis()
            val localId = generateLocalId()
            
            val response = client.get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate") {
                parameter("source", "main-mini")
                parameter("local_id", localId)
                parameter("ts", timestamp)
            }
            
            val result = response.body<BiliQrResponse>()
            
            if (result.code == 0 && result.data != null) {
                QrCodeResult.Success(
                    qrcodeKey = result.data.qrcode_key,
                    qrUrl = result.data.url
                )
            } else {
                QrCodeResult.Error(result.message ?: "获取二维码失败")
            }
        } catch (e: Exception) {
            QrCodeResult.Error("网络请求失败: ${e.message}")
        }
    }
    
    /**
     * 轮询扫码状态
     * @return 扫码状态
     */
    suspend fun pollLoginStatus(qrcodeKey: String): LoginStatus {
        return try {
            val timestamp = currentTimeMillis()
            val response = client.get("https://passport.bilibili.com/x/passport-login/web/qrcode/poll") {
                parameter("qrcode_key", qrcodeKey)
                parameter("source", "main-fe-header")
                parameter("_", timestamp)
            }
            
            val result = response.body<BiliPollResponse>()
            
            if (result.code != 0) {
                return LoginStatus.Error(result.message ?: "请求失败")
            }
            
            when (result.data?.code) {
                86101 -> LoginStatus.WaitingScan
                86090 -> LoginStatus.WaitingConfirm
                86038 -> LoginStatus.Expired
                0 -> {
                    val url = result.data?.url ?: ""
                    if (url.isEmpty()) {
                        LoginStatus.Error("登录成功但未返回凭证")
                    } else {
                        val cookies = extractCookies(url)
                        if (cookies.isValid()) {
                            LoginStatus.Success(cookies)
                        } else {
                            LoginStatus.Error("未能提取有效凭证")
                        }
                    }
                }
                else -> LoginStatus.Error(result.data?.message ?: "未知错误 (code: ${result.data?.code})")
            }
        } catch (e: Exception) {
            LoginStatus.Error("网络请求失败: ${e.message}")
        }
    }
    
    /**
     * 完整的扫码登录流程
     * @param onQrCode 二维码生成回调，返回二维码URL供显示
     * @param onStatus 状态更新回调
     * @return 登录凭证
     */
    suspend fun loginWithQrCode(
        onQrCode: (String) -> Unit,
        onStatus: (LoginStatus) -> Unit
    ): BiliCookies? {
        // 1. 获取二维码
        val qrResult = getLoginQrCode()
        if (qrResult !is QrCodeResult.Success) {
            onStatus(LoginStatus.Error("获取二维码失败"))
            return null
        }
        
        onQrCode(qrResult.qrUrl)
        onStatus(LoginStatus.WaitingScan)
        
        // 2. 轮询状态（最多180秒，每3秒轮询一次）
        var attempt = 0
        val maxAttempts = 60  // 180秒 / 3秒
        
        while (attempt < maxAttempts) {
            delay(3000)
            attempt++
            
            val status = pollLoginStatus(qrResult.qrcodeKey)
            onStatus(status)
            
            when (status) {
                is LoginStatus.Success -> return status.cookies
                is LoginStatus.Expired -> return null
                is LoginStatus.Error -> return null
                else -> continue
            }
        }
        
        onStatus(LoginStatus.Expired)
        return null
    }
    
    /**
     * 从登录成功URL中提取Cookie
     */
    private fun extractCookies(url: String): BiliCookies {
        // URL格式：https://...?SESSDATA=xxx&bili_jct=xxx&...
        val params = url.substringAfter("?", "")
            .split("&")
            .mapNotNull { 
                val parts = it.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null 
            }
            .toMap()
        
        return BiliCookies(
            sessdata = params["SESSDATA"] ?: "",
            biliJct = params["bili_jct"] ?: "",
            dedeUserId = params["DedeUserID"] ?: "",
            dedeUserIdCkMd5 = params["DedeUserID__ckMd5"] ?: "",
            sid = params["sid"] ?: ""
        )
    }
}

// ==================== 数据类 ====================

/**
 * 二维码获取结果
 */
sealed class QrCodeResult {
    data class Success(
        val qrcodeKey: String,
        val qrUrl: String
    ) : QrCodeResult()
    
    data class Error(val message: String) : QrCodeResult()
}

/**
 * 登录状态
 */
sealed class LoginStatus {
    object WaitingScan : LoginStatus()      // 等待扫码
    object WaitingConfirm : LoginStatus()   // 等待确认
    object Expired : LoginStatus()          // 二维码过期
    data class Success(val cookies: BiliCookies) : LoginStatus()
    data class Error(val message: String) : LoginStatus()
}

/**
 * B站Cookie凭证
 */
data class BiliCookies(
    val sessdata: String,
    val biliJct: String,
    val dedeUserId: String,
    val dedeUserIdCkMd5: String,
    val sid: String
) {
    /**
     * 转换为Cookie字符串，用于HTTP请求
     */
    fun toCookieString(): String {
        return buildString {
            if (sessdata.isNotEmpty()) append("SESSDATA=$sessdata; ")
            if (biliJct.isNotEmpty()) append("bili_jct=$biliJct; ")
            if (dedeUserId.isNotEmpty()) append("DedeUserID=$dedeUserId; ")
            if (dedeUserIdCkMd5.isNotEmpty()) append("DedeUserID__ckMd5=$dedeUserIdCkMd5; ")
            if (sid.isNotEmpty()) append("sid=$sid; ")
        }.trimEnd(';', ' ')
    }
    
    fun isValid(): Boolean = sessdata.isNotEmpty()
}

/**
 * 生成local_id（B站设备标识）
 */
private fun generateLocalId(): String {
    return buildString {
        repeat(8) { append((0..15).random().toString(16)) }
        append("-")
        repeat(4) { append((0..15).random().toString(16)) }
        append("-")
        repeat(4) { append((0..15).random().toString(16)) }
        append("-")
        repeat(4) { append((0..15).random().toString(16)) }
        append("-")
        repeat(12) { append((0..15).random().toString(16)) }
    }.uppercase()
}

// ==================== API响应数据类 ====================

@Serializable
data class BiliQrResponse(
    val code: Int = 0,
    val message: String = "",
    val data: QrData? = null
)

@Serializable
data class QrData(
    val url: String = "",
    val qrcode_key: String = ""
)

@Serializable
data class BiliPollResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PollData? = null
)

@Serializable
data class PollData(
    val url: String = "",
    val refresh_token: String = "",
    val timestamp: Long = 0,
    val code: Int = 0,
    val message: String = ""
)

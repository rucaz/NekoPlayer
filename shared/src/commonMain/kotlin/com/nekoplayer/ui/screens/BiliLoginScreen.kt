package com.nekoplayer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nekoplayer.data.api.BiliCookies
import com.nekoplayer.data.api.BiliLoginApi
import com.nekoplayer.data.api.LoginStatus
import com.nekoplayer.data.api.QrCodeResult
import io.ktor.client.engine.*
import kotlinx.coroutines.launch

/**
 * B站扫码登录界面
 */
@Composable
fun BiliLoginScreen(
    engine: HttpClientEngine,
    onLoginSuccess: (BiliCookies) -> Unit,
    onSkip: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val loginApi = remember { BiliLoginApi(engine) }
    
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var loginStatus by remember { mutableStateOf<LoginStatus>(LoginStatus.WaitingScan) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 开始扫码流程
    LaunchedEffect(Unit) {
        startLoginFlow(
            loginApi = loginApi,
            onQrCode = { url -> qrUrl = url },
            onStatus = { status -> loginStatus = status },
            onSuccess = onLoginSuccess
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // 标题
            Text(
                text = "B站登录",
                color = Color.White,
                fontSize = 28.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "使用哔哩哔哩APP扫码登录",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // 二维码区域
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = Color(0xFF00D4FF))
                    }
                    qrUrl != null -> {
                        // 使用在线二维码生成服务
                        // 或者后续接入QRCode生成库
                        AsyncImage(
                            model = "https://api.qrserver.com/v1/create-qr-code/?size=240x240&data=$qrUrl",
                            contentDescription = "登录二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Text(
                            text = "加载中...",
                            color = Color.Black
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 状态提示
            StatusText(status = loginStatus)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 刷新按钮
            if (loginStatus is LoginStatus.Expired || loginStatus is LoginStatus.Error) {
                Button(
                    onClick = {
                        scope.launch {
                            startLoginFlow(
                                loginApi = loginApi,
                                onQrCode = { url -> qrUrl = url },
                                onStatus = { status -> loginStatus = status },
                                onSuccess = onLoginSuccess
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D4FF)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "重新获取",
                        color = Color.Black
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 跳过按钮（免登录模式）
            TextButton(onClick = onSkip) {
                Text(
                    text = "暂不登录，使用免登录模式",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * 状态提示文本
 */
@Composable
private fun StatusText(status: LoginStatus) {
    val (icon, text, color) = when (status) {
        is LoginStatus.WaitingScan -> Triple(
            "📱", 
            "请使用哔哩哔哩APP扫码",
            Color(0xFF00D4FF)
        )
        is LoginStatus.WaitingConfirm -> Triple(
            "✅",
            "扫码成功，请在APP中确认登录",
            Color(0xFF39FF14)
        )
        is LoginStatus.Expired -> Triple(
            "⏰",
            "二维码已过期，请刷新重试",
            Color(0xFFFF3333)
        )
        is LoginStatus.Success -> Triple(
            "🎉",
            "登录成功！",
            Color(0xFF39FF14)
        )
        is LoginStatus.Error -> Triple(
            "❌",
            (status as LoginStatus.Error).message,
            Color(0xFFFF3333)
        )
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = icon,
            fontSize = 32.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 启动登录流程
 */
private suspend fun startLoginFlow(
    loginApi: BiliLoginApi,
    onQrCode: (String) -> Unit,
    onStatus: (LoginStatus) -> Unit,
    onSuccess: (BiliCookies) -> Unit
) {
    val cookies = loginApi.loginWithQrCode(
        onQrCode = onQrCode,
        onStatus = onStatus
    )
    
    if (cookies != null && cookies.isValid()) {
        onSuccess(cookies)
    }
}

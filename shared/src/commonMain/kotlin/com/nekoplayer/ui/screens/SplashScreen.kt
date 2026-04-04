package com.nekoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.nekoplayer.data.repository.UserRepository
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * 启动页 - 检查登录状态并跳转
 */
class SplashScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val userRepository: UserRepository = koinInject()
        
        LaunchedEffect(Unit) {
            delay(1500) // 显示1.5秒启动页
            
            // 检查是否已登录
            val isLoggedIn = userRepository.isLoggedIn()
            
            if (isLoggedIn) {
                // 已登录，直接到搜索页
                navigator.replace(SearchScreen())
            } else {
                // 未登录，到登录页
                navigator.replace(LoginEntryScreen())
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "NekoPlayer",
                    color = Color(0xFF00D4FF),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "赛博朋克音乐播放器",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                CircularProgressIndicator(
                    color = Color(0xFF00D4FF),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

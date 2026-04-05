package com.nekoplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.MiniPlayer
import com.nekoplayer.ui.screens.SplashScreen
import org.koin.compose.koinInject

/**
 * App主题颜色
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),  // 赛博青
    secondary = Color(0xFF9C27B0), // 紫色
    background = Color(0xFF0A0A0F),
    surface = Color(0xFF1A1A2F),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

/**
 * App入口 - 集成全局MiniPlayer
 */
@Composable
fun App() {
    MaterialTheme(
        colorScheme = DarkColorScheme
    ) {
        Navigator(
            screen = SplashScreen()
        ) { navigator ->
            // 初始化 AudioPlayer 和 QueueManager 的关联
            val player: AudioPlayer = koinInject()
            val queueManager: QueueManager = koinInject()

            LaunchedEffect(Unit) {
                player.setQueueManager(queueManager)
            }

            // 主布局：内容区域 + 底部MiniPlayer
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(0xFF0A0A0F),
                bottomBar = {
                    MiniPlayer(navigator = navigator)
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    SlideTransition(navigator)
                }
            }
        }
    }
}

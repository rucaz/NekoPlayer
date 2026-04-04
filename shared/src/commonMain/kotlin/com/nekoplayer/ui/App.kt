package com.nekoplayer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.nekoplayer.ui.screens.SplashScreen

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
 * App入口 - 使用disposeSteps防止搜索页被销毁
 */
@Composable
fun App() {
    MaterialTheme(
        colorScheme = DarkColorScheme
    ) {
        Navigator(
            screen = SplashScreen(),
            disposeSteps = false  // 保留导航栈中的页面，不自动销毁
        ) { navigator ->
            SlideTransition(navigator)
        }
    }
}

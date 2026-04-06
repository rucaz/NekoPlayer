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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.nekoplayer.data.api.BilibiliApi
import com.nekoplayer.data.api.MiguApi
import com.nekoplayer.data.model.MusicSource
import com.nekoplayer.data.repository.PlaybackRepository
import com.nekoplayer.data.repository.PlaybackStateSaver
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.MiniPlayer
import com.nekoplayer.ui.screens.SplashScreen
import kotlinx.coroutines.delay
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
            val playbackRepository: PlaybackRepository = koinInject()
            val bilibiliApi: BilibiliApi = koinInject()
            val miguApi: MiguApi = koinInject()

            // 恢复上次播放状态并启动自动保存
            LaunchedEffect(Unit) {
                player.setQueueManager(queueManager)

                // 延迟一点等待数据库初始化完成
                delay(500)

                // 加载上次保存的播放状态
                val restoredState = playbackRepository.loadPlaybackState()
                restoredState?.let { state ->
                    if (state.queue.isNotEmpty()) {
                        // 恢复播放队列
                        val currentIndex = state.queue.indexOfFirst { it.id == state.currentSongId }
                            .coerceAtLeast(0)
                        queueManager.playQueue(state.queue, currentIndex)
                        queueManager.setPlayMode(state.playMode)

                        // 准备播放（但不自动播放）
                        state.queue.getOrNull(currentIndex)?.let { song ->
                            // 如果 playUrl 为空，尝试获取
                            val songWithUrl = if (song.playUrl.isNullOrEmpty()) {
                                try {
                                    val playUrl = when (song.source) {
                                        MusicSource.MIGU -> miguApi.getPlayUrl(song.sourceId)
                                        else -> bilibiliApi.getPlayUrl(song.sourceId)
                                    }
                                    playUrl?.let { song.copy(playUrl = it) }
                                } catch (e: Exception) { null }
                            } else song

                            if (songWithUrl?.playUrl != null) {
                                player.prepare(songWithUrl)
                                // 恢复播放位置
                                player.seekTo(state.currentPosition)
                            }
                        }
                    }
                }

                // 启动自动保存
                val stateSaver = PlaybackStateSaver(playbackRepository, player, queueManager)
                stateSaver.start()
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

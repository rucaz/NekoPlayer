package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Custom icons using filled style
private val Icons.Filled.SkipPrevious: ImageVector
    get() = Icons.Filled.ArrowBack // Placeholder - we'll create proper icons below

private val Icons.Filled.SkipNext: ImageVector
    get() = Icons.Filled.ArrowBack // Placeholder

private val Icons.Filled.Pause: ImageVector
    get() = Icons.Filled.PlayArrow // Placeholder

/**
 * 正在播放界面 - Voyager Screen
 * 参考设计风格：高斯模糊背景 + 波形可视化
 */
data class NowPlayingScreen(val song: Song) : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val player: AudioPlayer = koinInject()
        
        val playerState by player.playerState.collectAsState()
        val currentPosition by player.currentPosition.collectAsState()
        val waveformData by player.waveformData.collectAsState()
        
        // 页面加载时准备播放
        LaunchedEffect(song) {
            player.prepare(song)
            delay(500)
            player.play()
        }
        
        // 页面离开时释放资源
        DisposableEffect(Unit) {
            onDispose {
                // 不释放，允许后台播放
            }
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景层：模糊封面
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(30.dp)
            )
            
            // 暗色蒙版
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
            
            // 内容层
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部导航
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "正在播放",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    
                    IconButton(onClick = { /* 更多选项 */ }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 封面 + 波形区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                ) {
                    // 清晰封面
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize(0.7f)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    
                    // 音频波形
                    WaveformVisualizer(
                        waveformData = waveformData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 歌曲信息
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 进度条
                val duration = if (song.duration > 0) song.duration else 180000L
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = currentPosition.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { player.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00D4FF),
                            activeTrackColor = Color(0xFF00D4FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 控制按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 上一首
                    IconButton(
                        onClick = { /* 上一首 */ },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            text = "⏮",
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                    
                    // 播放/暂停
                    val isPlaying = playerState is PlayerState.Playing
                    FilledIconButton(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF00D4FF).copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            color = Color.Black,
                            fontSize = 28.sp
                        )
                    }
                    
                    // 下一首
                    IconButton(
                        onClick = { /* 下一首 */ },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            text = "⏭",
                            color = Color.White,
                            fontSize = 24.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 波形可视化组件
 */
@Composable
fun WaveformVisualizer(
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    // 如果没有数据，显示模拟动画
    val displayData = if (waveformData.all { it == 0f }) {
        List(64) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val value by infiniteTransition.animateFloat(
                initialValue = 0.1f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500 + index * 10, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            value
        }
    } else {
        waveformData.map { (it + 1f) / 2f } // 转换到 0-1 范围
    }
    
    Canvas(modifier = modifier) {
        val barCount = 32 // 显示的条形数量
        val barWidth = size.width / (barCount * 1.5f)
        val gap = barWidth * 0.5f
        
        val step = displayData.size / barCount
        
        for (i in 0 until barCount) {
            val dataIndex = (i * step).coerceIn(0, displayData.size - 1)
            val amplitude = displayData[dataIndex].coerceIn(0.1f, 1f)
            
            val barHeight = size.height * amplitude
            val x = i * (barWidth + gap) + (size.width - barCount * (barWidth + gap)) / 2
            val y = (size.height - barHeight) / 2
            
            // 渐变色条
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00D4FF).copy(alpha = 0.8f),
                        Color(0xFF9C27B0).copy(alpha = 0.8f)
                    ),
                    startY = y,
                    endY = y + barHeight
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
        }
    }
}

/**
 * 格式化时间（毫秒 -> mm:ss）
 */
private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}

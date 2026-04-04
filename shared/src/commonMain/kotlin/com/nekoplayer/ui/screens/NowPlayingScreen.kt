package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * 模块化播放界面 - 参考赛博朋克音乐播放器设计
 * 布局：左侧大封面 + 右侧模块化控制面板
 */
data class NowPlayingScreen(val song: Song) : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player: AudioPlayer = koinInject()
        
        val playerState by player.playerState.collectAsState()
        val currentPosition by player.currentPosition.collectAsState()
        val duration by player.duration.collectAsState()
        val waveformData by player.waveformData.collectAsState()
        
        val actualDuration = if (duration > 0) duration else song.duration
        
        // 页面加载时准备播放
        LaunchedEffect(song) {
            player.prepare(song)
            delay(500)
            player.play()
        }
        
        Box(modifier = Modifier.fillMaxSize()) {
            // ========== 背景层 ==========
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.7f)
            )
            
            // 暗色渐变蒙版
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0A0F).copy(alpha = 0.3f),
                                Color(0xFF0A0A0F).copy(alpha = 0.85f),
                                Color(0xFF0A0A0F).copy(alpha = 0.95f)
                            )
                        )
                    )
            )
            
            // 网格装饰线
            GridDecoration()
            
            // ========== 内容层 ==========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // 顶部导航栏
                TopBar(onBack = { navigator.pop() })
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // 主内容区 - 左右布局
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 左侧：大封面 + 旋转装饰
                    CoverSection(
                        song = song,
                        isPlaying = playerState is PlayerState.Playing,
                        modifier = Modifier.weight(1.2f)
                    )
                    
                    // 右侧：模块化控制面板
                    ControlPanel(
                        song = song,
                        player = player,
                        playerState = playerState,
                        currentPosition = currentPosition,
                        duration = actualDuration,
                        waveformData = waveformData,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮 - 圆形玻璃质感
        GlassButton(
            onClick = onBack,
            icon = Icons.Default.ArrowBack,
            contentDescription = "返回"
        )
        
        // 标题 - 霓虹风格
        Text(
            text = "NekoPlayer",
            color = Color(0xFF00D4FF),
            fontSize = 20.sp,
            modifier = Modifier.alpha(0.9f)
        )
        
        // 菜单按钮
        GlassButton(
            onClick = { },
            icon = Icons.Default.MoreVert,
            contentDescription = "更多"
        )
    }
}

@Composable
private fun CoverSection(
    song: Song,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        // 旋转装饰环
        if (isPlaying) {
            val infiniteTransition = rememberInfiniteTransition()
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .aspectRatio(1f)
                    .rotate(rotation)
            ) {
                // 外圈装饰
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF00D4FF).copy(alpha = 0.3f),
                        style = Stroke(width = 2.dp.toPx()),
                        radius = size.minDimension / 2 - 4.dp.toPx()
                    )
                    
                    // 刻度线
                    for (i in 0 until 60 step 5) {
                        val angle = Math.toRadians(i * 6.0 - 90)
                        val startRadius = size.minDimension / 2 - 16.dp.toPx()
                        val endRadius = size.minDimension / 2 - 8.dp.toPx()
                        
                        drawLine(
                            color = Color(0xFF00D4FF).copy(alpha = 0.5f),
                            start = Offset(
                                center.x + kotlin.math.cos(angle).toFloat() * startRadius,
                                center.y + kotlin.math.sin(angle).toFloat() * startRadius
                            ),
                            end = Offset(
                                center.x + kotlin.math.cos(angle).toFloat() * endRadius,
                                center.y + kotlin.math.sin(angle).toFloat() * endRadius
                            ),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
        }
        
        // 封面图片 - 带发光效果
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A2F))
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // 内阴影效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f)
                            ),
                            radius = 0.8f
                        )
                    )
            )
        }
    }
}

@Composable
private fun ControlPanel(
    song: Song,
    player: AudioPlayer,
    playerState: PlayerState,
    currentPosition: Long,
    duration: Long,
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // ========== 模块1：歌曲信息卡片 ==========
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        // ========== 模块2：波形可视化 ==========
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "AUDIO VISUALIZER",
                    color = Color(0xFF00D4FF).copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                WaveformVisualizer(
                    waveformData = waveformData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            }
        }
        
        // ========== 模块3：进度控制 ==========
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                // 进度条
                CustomSlider(
                    value = currentPosition.toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color(0xFF00D4FF),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // ========== 模块4：播放控制 ==========
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首
            ControlButton(
                onClick = { },
                icon = Icons.Default.SkipPrevious,
                size = 48.dp
            )
            
            // 播放/暂停
            val isPlaying = playerState is PlayerState.Playing
            NeonPlayButton(
                isPlaying = isPlaying,
                onClick = {
                    if (isPlaying) player.pause() else player.play()
                }
            )
            
            // 下一首
            ControlButton(
                onClick = { },
                icon = Icons.Default.SkipNext,
                size = 48.dp
            )
        }
    }
}

// ========== 组件 ==========

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Color(0xFF1A1A2F).copy(alpha = 0.6f)
            )
            .padding(1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            content()
        }
    }
}

@Composable
private fun GlassButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun NeonPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val neonColor = Color(0xFF00D4FF)
    
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        neonColor.copy(alpha = 0.3f),
                        neonColor.copy(alpha = 0.1f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 发光边框
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(neonColor.copy(alpha = 0.2f))
        )
        
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = neonColor,
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun ControlButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val progress = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    
    Box(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
    ) {
        // 背景轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .align(Alignment.Center)
        )
        
        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00D4FF),
                            Color(0xFF9C27B0)
                        )
                    )
                )
                .align(Alignment.CenterStart)
        )
        
        // 滑块
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (progress * 1000).dp / 10) // 简化计算
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00D4FF))
                    .clickable { }
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    val displayData = if (waveformData.all { it == 0f }) {
        List(32) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val value by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400 + index * 20, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            value
        }
    } else {
        waveformData.map { (it + 1f) / 2f }
    }
    
    Canvas(modifier = modifier) {
        val barCount = 20
        val barWidth = size.width / (barCount * 1.8f)
        val gap = barWidth * 0.8f
        
        val step = displayData.size / barCount
        
        for (i in 0 until barCount) {
            val dataIndex = (i * step).coerceIn(0, displayData.size - 1)
            val amplitude = displayData[dataIndex].coerceIn(0.1f, 1f)
            
            val barHeight = size.height * amplitude
            val x = i * (barWidth + gap) + (size.width - barCount * (barWidth + gap)) / 2
            val y = (size.height - barHeight) / 2
            
            // 圆角条形
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00D4FF).copy(alpha = 0.9f),
                        Color(0xFF9C27B0).copy(alpha = 0.7f)
                    ),
                    startY = y,
                    endY = y + barHeight
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

@Composable
private fun GridDecoration() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val lineColor = Color(0xFF00D4FF).copy(alpha = 0.03f)
        val step = 40.dp.toPx()
        
        // 垂直线
        for (x in 0..size.width.toInt() step step.toInt()) {
            drawLine(
                color = lineColor,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height),
                strokeWidth = 1f
            )
        }
        
        // 水平线
        for (y in 0..size.height.toInt() step step.toInt()) {
            drawLine(
                color = lineColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1f
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(minutes, secs)
}

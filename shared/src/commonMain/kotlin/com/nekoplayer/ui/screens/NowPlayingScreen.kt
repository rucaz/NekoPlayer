package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.api.BilibiliApi
import com.nekoplayer.data.api.MiguApi
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * 模块化播放界面 - 参考赛博朋克音乐播放器设计
 * 布局：左侧大封面 + 右侧模块化控制面板
 * 现在支持队列播放，从 QueueManager 获取当前歌曲
 */
class NowPlayingScreen(private val initialSong: Song? = null) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val player: AudioPlayer = koinInject()
        val queueManager: QueueManager = koinInject()
        val bilibiliApi: BilibiliApi = koinInject()
        val miguApi: MiguApi = koinInject()

        // 从 QueueManager 获取当前歌曲，如果没有则使用初始歌曲
        val queueCurrentSong by queueManager.currentSong.collectAsState()
        val currentSong = queueCurrentSong ?: initialSong

        val playerState by player.playerState.collectAsState()
        val currentPosition by player.currentPosition.collectAsState()
        val duration by player.duration.collectAsState()
        val waveformData by player.waveformData.collectAsState()

        // 当队列中的歌曲变化时，准备播放新歌曲
        LaunchedEffect(queueCurrentSong) {
            queueCurrentSong?.let { song ->
                // 只要当前播放器里的歌曲和队列歌曲不一致，就重新准备
                val currentPlayerSong = (playerState as? PlayerState.Playing)?.song 
                    ?: (playerState as? PlayerState.Paused)?.song
                
                if (currentPlayerSong?.id != song.id) {
                    // 如果 playUrl 为空，先获取
                    val songWithUrl = if (song.playUrl.isNullOrEmpty()) {
                        try {
                            val playUrl = when (song.source) {
                                com.nekoplayer.data.model.MusicSource.MIGU -> 
                                    miguApi.getPlayUrl(song.sourceId)
                                else -> 
                                    bilibiliApi.getPlayUrl(song.sourceId)
                            }
                            playUrl?.let { song.copy(playUrl = it) }
                        } catch (e: Exception) { null }
                    } else song
                    
                    if (songWithUrl?.playUrl.isNullOrEmpty()) {
                        // 获取播放链接失败
                        return@let
                    }
                    
                    player.prepare(songWithUrl!!)
                    delay(300)
                    player.play()
                }
            }
        }

        // 初始歌曲的播放准备（当队列为空时）
        LaunchedEffect(Unit) {
            if (queueCurrentSong == null && initialSong != null) {
                queueManager.playQueue(listOf(initialSong), 0)
            }
        }

        currentSong?.let { song ->
            val actualDuration = if (duration > 0) duration else song.duration

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
                    // 顶部导航栏 - 移除标题，只保留返回和队列按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassButton(
                            onClick = { navigator.pop() },
                            icon = Icons.Default.ArrowBack,
                            contentDescription = "返回"
                        )

                        Row {
                            // 添加到歌单按钮
                            var showAddToPlaylist by remember { mutableStateOf(false) }
                            
                            GlassButton(
                                onClick = { showAddToPlaylist = true },
                                icon = Icons.Default.Add,
                                contentDescription = "添加到歌单"
                            )
                            
                            if (showAddToPlaylist) {
                                com.nekoplayer.ui.components.AddToPlaylistDialog(
                                    song = song,
                                    onDismiss = { showAddToPlaylist = false }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            GlassButton(
                                onClick = { navigator.push(NowPlayingQueueScreen()) },
                                icon = Icons.Default.List,
                                contentDescription = "播放队列"
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            val playMode by queueManager.playMode.collectAsState()
                            GlassButton(
                                onClick = { queueManager.togglePlayMode() },
                                icon = when (playMode) {
                                    QueueManager.PlayMode.SEQUENTIAL -> Icons.Default.Repeat
                                    QueueManager.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                                    QueueManager.PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                                },
                                contentDescription = "播放模式"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                            waveformData = waveformData,
                            modifier = Modifier.weight(1.2f)
                        )

                        // 右侧：模块化控制面板
                        ControlPanel(
                            song = song,
                            player = player,
                            queueManager = queueManager,
                            playerState = playerState,
                            currentPosition = currentPosition,
                            duration = actualDuration,
                            waveformData = waveformData,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        } ?: run {
            // 没有歌曲时显示空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有正在播放的歌曲",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, onQueueClick: () -> Unit) {
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

        Row {
            // 队列按钮
            GlassButton(
                onClick = onQueueClick,
                icon = Icons.Default.List,
                contentDescription = "播放队列"
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 播放模式按钮
            val queueManager: QueueManager = koinInject()
            val playMode by queueManager.playMode.collectAsState()

            GlassButton(
                onClick = { queueManager.togglePlayMode() },
                icon = when (playMode) {
                    QueueManager.PlayMode.SEQUENTIAL -> Icons.Default.Repeat
                    QueueManager.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                    QueueManager.PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                },
                contentDescription = "播放模式"
            )
        }
    }
}

@Composable
private fun CoverSection(
    song: Song,
    isPlaying: Boolean,
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        // 始终旋转的装饰环（科技感更强）
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isPlaying) 8000 else 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )

        // 外圈频谱环（仅在播放时显示）
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .aspectRatio(1f)
            ) {
                // 使用真实音频数据或模拟的呼吸效果
                val hasRealData = waveformData.any { kotlin.math.abs(it) > 0.01f }
                
                // 如果没有真实数据，使用呼吸动画
                val breathingAnim = rememberInfiniteTransition()
                val breathValue by breathingAnim.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val baseRadius = size.minDimension / 2 - 4.dp.toPx()
                    val barCount = 60
                    val step = 360f / barCount

                    for (i in 0 until barCount) {
                        // 根据是否有真实数据选择显示方式
                        val amplitude = if (hasRealData) {
                            // 使用真实音频数据
                            val dataIndex = (i * waveformData.size / barCount).coerceIn(0, waveformData.size - 1)
                            val rawValue = waveformData[dataIndex]
                            // 将 -1~1 映射到 0.1~1.0
                            ((rawValue + 1f) / 2f).coerceIn(0.1f, 1f)
                        } else {
                            // 使用呼吸动画 + 位置偏移产生波浪感
                            val offset = i.toFloat() / barCount
                            (breathValue + sin((offset * 4.0 * PI).toFloat()) * 0.1f).coerceIn(0.15f, 0.5f)
                        }
                        
                        val barLength = 20.dp.toPx() * amplitude

                        val angle = (i * step - 90) * PI / 180.0
                        val cosA = cos(angle).toFloat()
                        val sinA = sin(angle).toFloat()

                        val startX = centerX + cosA * baseRadius
                        val startY = centerY + sinA * baseRadius
                        val endX = centerX + cosA * (baseRadius + barLength)
                        val endY = centerY + sinA * (baseRadius + barLength)

                        // 真实数据时使用更亮的颜色
                        val alpha = if (hasRealData) 0.9f else 0.5f
                        
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D4FF).copy(alpha = alpha),
                                    Color(0xFF9C27B0).copy(alpha = alpha * 0.7f)
                                ),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY)
                            ),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = if (hasRealData) 2.5.dp.toPx() else 1.5f.dp.toPx()
                        )
                    }
                }
            }
        }

        // 旋转的装饰环
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(1f)
                .rotate(rotation)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 外圈霓虹环
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF00D4FF).copy(alpha = 0.8f),
                            Color(0xFF9C27B0).copy(alpha = 0.6f),
                            Color(0xFF00D4FF).copy(alpha = 0.8f)
                        ),
                        center = center
                    ),
                    style = Stroke(width = 3.dp.toPx()),
                    radius = size.minDimension / 2 - 4.dp.toPx()
                )

                // 内圈虚线
                drawCircle(
                    color = Color(0xFF00D4FF).copy(alpha = 0.4f),
                    style = Stroke(width = 1.dp.toPx()),
                    radius = size.minDimension / 2 - 20.dp.toPx()
                )

                // 科技刻度
                for (i in 0 until 24) {
                    val angle = (i * 15.0 - 90) * PI / 180.0
                    val isMajor = i % 6 == 0
                    val startRadius = size.minDimension / 2 - (if (isMajor) 16.dp.toPx() else 12.dp.toPx())
                    val endRadius = size.minDimension / 2 - 8.dp.toPx()

                    drawLine(
                        color = if (isMajor) Color(0xFF00D4FF).copy(alpha = 0.8f) else Color(0xFF00D4FF).copy(alpha = 0.4f),
                        start = Offset(
                            center.x + cos(angle).toFloat() * startRadius,
                            center.y + sin(angle).toFloat() * startRadius
                        ),
                        end = Offset(
                            center.x + cos(angle).toFloat() * endRadius,
                            center.y + sin(angle).toFloat() * endRadius
                        ),
                        strokeWidth = if (isMajor) 3.dp.toPx() else 1.5f.dp.toPx()
                    )
                }
            }
        }

        // 封面图片 - 不强求正方形，带发光效果
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A2F)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 霓虹边框
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 2.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00D4FF).copy(alpha = 0.6f),
                                Color(0xFF9C27B0).copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            // 内阴影效果
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.4f)
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
    queueManager: QueueManager,
    playerState: PlayerState,
    currentPosition: Long,
    duration: Long,
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Top
    ) {
        // ========== 模块1：歌曲信息卡片（放在最上面）==========
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    maxLines = 2,
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ========== 模块2：波形可视化（高度2倍）==========
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUDIO VISUALIZER",
                        color = Color(0xFF00D4FF).copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    
                    // 实时峰值指示
                    val peakValue = if (waveformData.isNotEmpty()) {
                        waveformData.maxOf { kotlin.math.abs(it) }
                    } else 0f
                    
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (peakValue > 0.5f) Color(0xFF00D4FF).copy(alpha = 0.9f)
                                else Color(0xFF00D4FF).copy(alpha = 0.3f)
                            )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                WaveformVisualizer(
                    waveformData = waveformData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)  // 2倍高度
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ========== 模块4：播放控制 ==========
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首
            ControlButton(
                onClick = { player.playPrevious() },
                icon = Icons.Default.SkipPrevious,
                size = 56.dp
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
                onClick = { player.playNext() },
                icon = Icons.Default.SkipNext,
                size = 56.dp
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
    var sliderWidth by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(value) }
    
    val currentValue = if (isDragging) dragValue else value
    val progress = ((currentValue - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
            .onSizeChanged { sliderWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / sliderWidth).coerceIn(0f, 1f)
                    val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { 
                        isDragging = true
                        dragValue = value
                    },
                    onDragEnd = { 
                        isDragging = false
                        onValueChange(dragValue)
                    },
                    onDragCancel = { isDragging = false },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val dragProgress = (change.position.x / sliderWidth).coerceIn(0f, 1f)
                        dragValue = valueRange.start + dragProgress * (valueRange.endInclusive - valueRange.start)
                    }
                )
            }
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
                .fillMaxWidth(progress)
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
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val thumbOffset = with(density) { (progress * sliderWidth).toDp() }
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset - 8.dp)
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF00D4FF))
                    .then(if (isDragging) Modifier.padding(2.dp) else Modifier)
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    waveformData: List<Float>,
    modifier: Modifier = Modifier
) {
    // 使用更自然的频谱动画
    val barCount = 24
    val animatedValues = List(barCount) { index ->
        val infiniteTransition = rememberInfiniteTransition()
        
        // 如果有真实数据，使用数据驱动
        val baseValue = if (waveformData.isNotEmpty() && !waveformData.all { it == 0f }) {
            val dataIndex = (index * waveformData.size / barCount).coerceIn(0, waveformData.size - 1)
            ((waveformData[dataIndex] + 1f) / 2f).coerceIn(0.2f, 1f)
        } else {
            0.3f
        }
        
        // 添加动态波动
        val animatedValue by infiniteTransition.animateFloat(
            initialValue = baseValue * 0.5f,
            targetValue = baseValue,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 300 + (index % 5) * 80,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            )
        )
        animatedValue
    }

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 1.5f)
        val gap = barWidth * 0.5f
        val totalWidth = barCount * (barWidth + gap)
        val startX = (size.width - totalWidth) / 2

        for (i in 0 until barCount) {
            val amplitude = animatedValues[i].coerceIn(0.1f, 1f)
            // 放大高度变化范围
            val barHeight = size.height * (0.15f + amplitude * 0.85f)
            
            val x = startX + i * (barWidth + gap)
            val y = (size.height - barHeight) / 2

            // 更科技感的渐变色
            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00FFFF).copy(alpha = 0.95f),  // 青色
                    Color(0xFF00D4FF).copy(alpha = 0.8f),   // 蓝色
                    Color(0xFF9C27B0).copy(alpha = 0.6f)    // 紫色
                ),
                startY = y,
                endY = y + barHeight
            )

            // 绘制发光效果的条形
            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
            
            // 添加顶部高光
            drawRoundRect(
                color = Color(0xFF00FFFF).copy(alpha = 0.6f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barWidth),
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
    return "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

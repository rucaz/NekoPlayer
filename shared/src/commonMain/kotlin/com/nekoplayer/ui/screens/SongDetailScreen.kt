package com.nekoplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.api.BiliSubtitleApi
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.StatsRepository
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 歌曲详情页
 * 
 * 展示歌曲完整信息：
 * - 大封面展示
 * - 歌曲元数据（标题、艺术家、专辑）
 * - 操作按钮（播放、添加到歌单、下一首播放）
 * - 播放统计（播放次数、总时长）
 * - 歌词入口（如果有）
 */
class SongDetailScreen(
    private val song: Song
) : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val queueManager: QueueManager = koinInject()
        val statsRepository: StatsRepository? = koinInjectOrNull()
        val biliSubtitleApi: BiliSubtitleApi? = koinInjectOrNull()
        val coroutineScope = rememberCoroutineScope()
        
        // 状态
        var showAddToPlaylist by remember { mutableStateOf(false) }
        var songStats by remember { mutableStateOf<SongStatsDisplay?>(null) }
        var isLoadingStats by remember { mutableStateOf(false) }
        var hasLyrics by remember { mutableStateOf(false) }
        var isCheckingLyrics by remember { mutableStateOf(false) }
        
        // 加载统计数据
        LaunchedEffect(song.id) {
            statsRepository?.let { repo ->
                isLoadingStats = true
                repo.getSongStats(song.id).firstOrNull()?.let { stats ->
                    songStats = SongStatsDisplay(
                        playCount = stats.totalPlays,
                        totalDuration = formatDuration(stats.totalDuration),
                        firstPlayed = stats.firstPlayedAt?.let { formatDate(it) },
                        lastPlayed = stats.lastPlayedAt?.let { formatDate(it) }
                    )
                }
                isLoadingStats = false
            }
        }
        
        // 检查是否有歌词（B站视频检查字幕）
        LaunchedEffect(song.sourceId) {
            if (song.source == com.nekoplayer.data.model.MusicSource.BILIBILI) {
                isCheckingLyrics = true
                biliSubtitleApi?.let { api ->
                    val subtitles = api.getSubtitleList(song.sourceId)
                    hasLyrics = subtitles.isNotEmpty()
                }
                isCheckingLyrics = false
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            // 背景模糊封面
            song.coverUrl?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp)
                        .alpha(0.3f),
                    contentScale = ContentScale.Crop
                )
            }
            
            // 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0A0F).copy(alpha = 0.3f),
                                Color(0xFF0A0A0F).copy(alpha = 0.9f),
                                Color(0xFF0A0A0F)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )
            
            // 内容
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                var showMoreMenu by remember { mutableStateOf(false) }
                
                DetailTopBar(
                    onBack = { navigator.pop() },
                    onMore = { showMoreMenu = true }
                )
                
                // 更多操作菜单
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                    containerColor = Color(0xFF1A1A2F)
                ) {
                    DropdownMenuItem(
                        text = { Text("分享", color = Color.White) },
                        onClick = {
                            showMoreMenu = false
                            // TODO: 分享功能
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("查看B站原视频", color = Color.White) },
                        onClick = {
                            showMoreMenu = false
                            // TODO: 打开B站链接
                        },
                        leadingIcon = {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White)
                        }
                    )
                }
                
                // 可滚动内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // 大封面
                    BigCoverCard(
                        coverUrl = song.coverUrl,
                        modifier = Modifier.size(240.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 歌曲信息
                    SongInfoSection(
                        title = song.title,
                        artist = song.artist,
                        album = song.album
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 操作按钮
                    ActionButtons(
                        onPlay = {
                            queueManager.playNow(song)
                        },
                        onAddToPlaylist = {
                            showAddToPlaylist = true
                        },
                        onPlayNext = {
                            queueManager.addToQueueNext(song)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 统计信息
                    if (songStats != null || isLoadingStats) {
                        StatsSection(
                            stats = songStats,
                            isLoading = isLoadingStats
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                    
                    // 歌词入口
                    LyricsEntryCard(
                        hasLyrics = hasLyrics,
                        isLoading = isCheckingLyrics,
                        onClick = {
                            if (hasLyrics) {
                                // 歌词在NowPlaying中显示，这里只提示
                                coroutineScope.launch {
                                    // 可以显示Toast提示用户在播放页查看歌词
                                }
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 相关推荐（占位）
                    RelatedSongsSection(
                        songs = emptyList(), // TODO: 获取相关歌曲
                        onSongClick = { /* TODO */ }
                    )
                    
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
            
            // 添加到歌单弹窗
            if (showAddToPlaylist) {
                AddToPlaylistDialog(
                    song = song,
                    onDismiss = { showAddToPlaylist = false }
                )
            }
        }
    }
}

/**
 * 顶部栏
 */
@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassButton(
            onClick = onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回"
        )
        
        Text(
            text = "歌曲详情",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        
        GlassButton(
            onClick = onMore,
            icon = Icons.Default.MoreVert,
            contentDescription = "更多"
        )
    }
}

/**
 * 大封面卡片
 */
@Composable
private fun BigCoverCard(
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1A1A2F))
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        // 边缘发光效果
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.3f)
                        )
                    )
                )
        )
    }
}

/**
 * 歌曲信息区域
 */
@Composable
private fun SongInfoSection(
    title: String,
    artist: String,
    album: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = artist,
            color = Color(0xFF00D4FF),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        
        if (!album.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "《$album》",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 操作按钮组
 */
@Composable
private fun ActionButtons(
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 添加到歌单
        ActionButton(
            icon = Icons.Default.PlaylistAdd,
            label = "加入歌单",
            onClick = onAddToPlaylist
        )
        
        // 播放按钮（主按钮）
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00D4FF),
                            Color(0xFF00A8CC)
                        )
                    )
                )
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                modifier = Modifier.size(36.dp),
                tint = Color.White
            )
        }
        
        // 下一首播放
        ActionButton(
            icon = Icons.Default.QueueMusic,
            label = "下一首",
            onClick = onPlayNext
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

/**
 * 统计信息区域
 */
@Composable
private fun StatsSection(
    stats: SongStatsDisplay?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "播放统计",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF00D4FF)
                    )
                }
            } else if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatItem(
                        value = "${stats.playCount}",
                        label = "播放次数"
                    )
                    
                    StatItem(
                        value = stats.totalDuration,
                        label = "总播放时长"
                    )
                    
                    stats.lastPlayed?.let { lastPlayed ->
                        StatItem(
                            value = lastPlayed,
                            label = "上次播放"
                        )
                    }
                }
            } else {
                Text(
                    text = "暂无播放记录",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color(0xFF00D4FF),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

/**
 * 歌词入口卡片
 */
@Composable
private fun LyricsEntryCard(
    hasLyrics: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasLyrics && !isLoading, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isLoading -> Color.White.copy(alpha = 0.03f)
                hasLyrics -> Color(0xFF00D4FF).copy(alpha = 0.1f)
                else -> Color.White.copy(alpha = 0.03f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF00D4FF),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = if (hasLyrics) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "歌词",
                        color = when {
                            isLoading -> Color.White.copy(alpha = 0.5f)
                            hasLyrics -> Color.White
                            else -> Color.White.copy(alpha = 0.5f)
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = when {
                            isLoading -> "检查中..."
                            hasLyrics -> "播放页可查看歌词"
                            else -> "暂无歌词"
                        },
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
            
            if (!isLoading && hasLyrics) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF)
                )
            }
        }
    }
}

/**
 * 相关推荐区域
 */
@Composable
private fun RelatedSongsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit
) {
    if (songs.isEmpty()) return
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "相似推荐",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // TODO: 实现相关歌曲列表
    }
}

/**
 * 玻璃按钮
 */
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

/**
 * 统计展示数据
 */
data class SongStatsDisplay(
    val playCount: Int,
    val totalDuration: String,
    val firstPlayed: String?,
    val lastPlayed: String?
)

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / 3600000
    val minutes = (durationMs % 3600000) / 60000
    
    return when {
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "少于1分钟"
    }
}

/**
 * 格式化日期
 */
private fun formatDate(timestamp: Long): String {
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestamp)
    val localDate = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    return "${localDate.monthNumber}月${localDate.dayOfMonth}日"
}

/**
 * 安全注入（可为null）
 */
@Composable
inline fun <reified T : Any> koinInjectOrNull(): T? {
    return try {
        koinInject()
    } catch (e: Exception) {
        null
    }
}

package com.nekoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.nekoplayer.data.repository.PlayHistoryRepository
import com.nekoplayer.data.repository.PlaylistRepository
import org.koin.compose.koinInject
import com.nekoplayer.database.Playlist
import com.nekoplayer.utils.currentTimeMillis
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 歌单列表页
 */
class PlaylistListScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val playlistRepository: PlaylistRepository = koinInject()
        val scope = rememberCoroutineScope()

        val playlists by playlistRepository.getAllPlaylists().collectAsState(initial = emptyList())
        val recentPlays by playHistoryRepository.getRecentPlays(limit = 500).collectAsState(initial = emptyList())
        
        var showCreateDialog by remember { mutableStateOf(false) }
        var showClearHistoryDialog by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                TopBar(
                    onBack = { navigator.pop() },
                    onCreateClick = { showCreateDialog = true }
                )

                // 虚拟歌单区域：最近播放
                if (recentPlays.isNotEmpty()) {
                    RecentPlaysCard(
                        songCount = recentPlays.size,
                        onClick = {
                            navigator.push(RecentPlaysScreen())
                        },
                        onClearClick = { showClearHistoryDialog = true }
                    )
                }

                if (playlists.isEmpty() && recentPlays.isEmpty()) {
                    // 空状态
                    EmptyState(onCreateClick = { showCreateDialog = true })
                } else {
                    // 歌单网格 - 横屏优化：更小的卡片
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    navigator.push(PlaylistDetailScreen(playlist.id))
                                }
                            )
                        }
                    }
                }
            }
        }

        // 新建歌单弹窗
        if (showCreateDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    scope.launch {
                        playlistRepository.createPlaylist(name)
                        showCreateDialog = false
                    }
                }
            )
        }
        
        // 清空历史确认弹窗
        if (showClearHistoryDialog) {
            ClearHistoryDialog(
                onDismiss = { showClearHistoryDialog = false },
                onConfirm = {
                    scope.launch {
                        playHistoryRepository.clearAll()
                        showClearHistoryDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        Text(
            text = "我的歌单",
            color = Color.White,
            fontSize = 20.sp
        )

        IconButton(onClick = onCreateClick) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "新建歌单",
                tint = Color(0xFF00D4FF)
            )
        }
    }
}

@Composable
private fun EmptyState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "还没有歌单",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "创建一个歌单来收藏喜欢的音乐",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onCreateClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00D4FF)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("创建歌单")
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        // 封面区域 - 缩小到 1/5 大小
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = if (playlist.coverUrl != null) {
                        Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                    } else {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00D4FF).copy(alpha = 0.3f),
                                Color(0xFF9C27B0).copy(alpha = 0.3f)
                            )
                        )
                    }
                )
        ) {
            if (playlist.coverUrl == null) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 歌单名称
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 歌曲数量（这里暂时显示更新时间）
        Text(
            text = formatDate(playlist.updatedAt),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建歌单",
                color = Color.White
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("歌单名称", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00D4FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("创建", color = Color(0xFF00D4FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A2F)
    )
}

private fun formatDate(timestamp: Long): String {
    val now = currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        diff < 604800000 -> "${diff / 86400000}天前"
        else -> {
            // 简单格式化日期，不依赖 Java API
            val daysAgo = diff / 86400000
            "${daysAgo}天前"
        }
    }
}

/**
 * 最近播放卡片（虚拟歌单）
 */
@Composable
private fun RecentPlaysCard(
    songCount: Int,
    onClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2F)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00D4FF).copy(alpha = 0.3f),
                                Color(0xFF9C27B0).copy(alpha = 0.3f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 文字信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "最近播放",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$songCount 首歌曲",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            
            // 清空按钮
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "清空历史",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // 箭头
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

/**
 * 清空历史确认弹窗
 */
@Composable
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "清空播放历史",
                color = Color.White
            )
        },
        text = {
            Text(
                text = "确定要清空所有播放历史记录吗？此操作不可恢复。",
                color = Color.White.copy(alpha = 0.7f)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm
            ) {
                Text("清空", color = Color(0xFFE91E63))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF1A1A2F)
    )
}
nerColor = Color(0xFF1A1A2F)
    )
}

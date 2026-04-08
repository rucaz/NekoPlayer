package com.nekoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.PlayHistoryRepository
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 最近播放页面（虚拟歌单）
 */
class RecentPlaysScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val playHistoryRepository: PlayHistoryRepository = koinInject()
        val queueManager: QueueManager = koinInject()
        
        val recentSongs by playHistoryRepository.getRecentPlays(limit = 500).collectAsState(initial = emptyList())
        val coroutineScope = rememberCoroutineScope()
        
        var showAddToPlaylist by remember { mutableStateOf<Song?>(null) }
        var showClearDialog by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("最近播放") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 清空按钮
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "清空")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (recentSongs.isEmpty()) {
                    EmptyRecentPlays()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 播放全部按钮
                        item {
                            PlayAllButton(
                                songCount = recentSongs.size,
                                onClick = {
                                    queueManager.playQueue(recentSongs, 0)
                                }
                            )
                        }
                        
                        // 歌曲列表
                        items(
                            items = recentSongs,
                            key = { it.id }
                        ) { song ->
                            RecentPlayItem(
                                song = song,
                                onClick = {
                                    val index = recentSongs.indexOfFirst { it.id == song.id }
                                    queueManager.playQueue(recentSongs, index.coerceAtLeast(0))
                                },
                                onAddToPlaylist = {
                                    showAddToPlaylist = song
                                },
                                onPlayNext = {
                                    queueManager.addToQueueNext(song)
                                }
                            )
                        }
                    }
                }
                
                // 添加到歌单弹窗
                showAddToPlaylist?.let { song ->
                    AddToPlaylistDialog(
                        song = song,
                        onDismiss = { showAddToPlaylist = null }
                    )
                }
                
                // 清空确认弹窗
                if (showClearDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearDialog = false },
                        title = { Text("清空播放历史") },
                        text = { Text("确定要清空所有播放历史记录吗？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        playHistoryRepository.clearAll()
                                        showClearDialog = false
                                    }
                                }
                            ) {
                                Text("清空", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearDialog = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyRecentPlays() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "暂无播放记录",
            style = MaterialTheme.typography.titleMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "播放的歌曲将出现在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

/**
 * 播放全部按钮
 */
@Composable
private fun PlayAllButton(
    songCount: Int,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text("播放全部 ($songCount)")
    }
}

/**
 * 最近播放列表项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentPlayItem(
    song: Song,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = song.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 来源标识
            val sourceColor = when (song.source.name) {
                "BILIBILI" -> Color(0xFFFB7299)
                "MIGU" -> Color(0xFF00A1D6)
                "LOCAL" -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.primary
            }
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(sourceColor.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = song.source.name.take(1),
                    color = sourceColor,
                    fontSize = 10.sp
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 更多菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("添加到歌单") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.PlaylistAdd, contentDescription = null)
                        }
                    )
                    
                    DropdownMenuItem(
                        text = { Text("下一首播放") },
                        onClick = {
                            showMenu = false
                            onPlayNext()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.QueueMusic, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

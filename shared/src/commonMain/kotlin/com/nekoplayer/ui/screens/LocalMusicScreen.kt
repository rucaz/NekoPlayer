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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.LocalSong
import com.nekoplayer.data.model.MusicSource
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.LocalMusicRepository
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 本地音乐页面
 */
class LocalMusicScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository: LocalMusicRepository = koinInject()
        val queueManager: QueueManager = koinInject()
        
        val localSongs by repository.getLocalSongs().collectAsState(initial = emptyList())
        val coroutineScope = rememberCoroutineScope()
        
        var isLoading by remember { mutableStateOf(false) }
        var hasPermission by remember { mutableStateOf(repository.hasPermission()) }
        var showAddToPlaylist by remember { mutableStateOf<Song?>(null) }
        
        // 首次进入时自动扫描
        LaunchedEffect(Unit) {
            if (hasPermission && localSongs.isEmpty()) {
                isLoading = true
                repository.scanMusic()
                isLoading = false
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("本地音乐") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 扫描按钮
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    repository.scanMusic()
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
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
                when {
                    !hasPermission -> PermissionRequestPanel(
                        onRequestPermission = {
                            coroutineScope.launch {
                                hasPermission = repository.requestPermission()
                                if (hasPermission) {
                                    repository.scanMusic()
                                }
                            }
                        }
                    )
                    
                    localSongs.isEmpty() && !isLoading -> EmptyLocalMusicPanel(
                        onScan = {
                            coroutineScope.launch {
                                isLoading = true
                                repository.scanMusic()
                                isLoading = false
                            }
                        }
                    )
                    
                    else -> LocalMusicList(
                        songs = localSongs,
                        onSongClick = { song ->
                            val queue = localSongs.map { it.toSong() }
                            val index = queue.indexOfFirst { it.id == song.id }
                            queueManager.playQueue(queue, index.coerceAtLeast(0))
                        },
                        onAddToPlaylist = { song ->
                            showAddToPlaylist = song
                        },
                        onPlayNext = { song ->
                            queueManager.addToQueueNext(song)
                        }
                    )
                }
                
                // 添加歌单弹窗
                showAddToPlaylist?.let { song ->
                    AddToPlaylistDialog(
                        song = song,
                        onDismiss = { showAddToPlaylist = null }
                    )
                }
            }
        }
    }
}

/**
 * 权限申请面板
 */
@Composable
private fun PermissionRequestPanel(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "需要存储权限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "请授予访问本地音乐文件的权限",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}

/**
 * 空状态面板
 */
@Composable
private fun EmptyLocalMusicPanel(
    onScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "暂无本地音乐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "点击扫描按钮搜索设备中的音乐文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onScan) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("扫描音乐")
        }
    }
}

/**
 * 本地音乐列表
 */
@Composable
private fun LocalMusicList(
    songs: List<LocalSong>,
    onSongClick: (LocalSong) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onPlayNext: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "共 ${songs.size} 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        items(
            items = songs,
            key = { it.id }
        ) { song ->
            LocalMusicItem(
                song = song,
                onClick = { onSongClick(song) },
                onAddToPlaylist = { onAddToPlaylist(song.toSong()) },
                onPlayNext = { onPlayNext(song.toSong()) }
            )
        }
    }
}

/**
 * 本地音乐列表项
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalMusicItem(
    song: LocalSong,
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
                if (song.coverPath != null) {
                    AsyncImage(
                        model = song.coverPath,
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
            
            // 时长
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
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

/**
 * 格式化时长
 */
private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60000
    val seconds = (durationMs % 60000) / 1000
    return String.format("%d:%02d", minutes, seconds)
}

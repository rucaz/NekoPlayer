package com.nekoplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.LocalMusicRepository
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.AddToPlaylistDialog
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 本地音乐页面 - 增强版
 * 
 * 功能：
 * - 扫描进度可视化
 * - 多维度排序（名称/艺术家/时长/添加时间）
 * - 文件夹筛选
 * - 批量操作
 * - 玻璃拟态UI
 */
class LocalMusicScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository: LocalMusicRepository = koinInject()
        val queueManager: QueueManager = koinInject()
        
        val localSongs by repository.getLocalSongs().collectAsState(initial = emptyList())
        val coroutineScope = rememberCoroutineScope()
        
        // 状态
        var isScanning by remember { mutableStateOf(false) }
        var scanProgress by remember { mutableStateOf(0f) }
        var scanMessage by remember { mutableStateOf("") }
        var hasPermission by remember { mutableStateOf(repository.hasPermission()) }
        var showAddToPlaylist by remember { mutableStateOf<Song?>(null) }
        var showSortMenu by remember { mutableStateOf(false) }
        var currentSort by remember { mutableStateOf(SortType.NAME_ASC) }
        var selectedFolder by remember { mutableStateOf<String?>(null) }
        var isBatchMode by remember { mutableStateOf(false) }
        val selectedSongs = remember { mutableStateListOf<LocalSong>() }
        
        // 文件夹列表
        val folders = remember(localSongs) {
            localSongs.map { it.folderPath }.distinct().sorted()
        }
        
        // 筛选和排序后的歌曲
        val displaySongs = remember(localSongs, currentSort, selectedFolder) {
            var filtered = if (selectedFolder != null) {
                localSongs.filter { it.folderPath == selectedFolder }
            } else localSongs
            
            when (currentSort) {
                SortType.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
                SortType.NAME_DESC -> filtered.sortedByDescending { it.title.lowercase() }
                SortType.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
                SortType.DURATION_DESC -> filtered.sortedByDescending { it.duration }
                SortType.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
            }
        }
        
        // 首次进入时自动扫描
        LaunchedEffect(Unit) {
            if (hasPermission && localSongs.isEmpty()) {
                isScanning = true
                repository.scanMusic()
                isScanning = false
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏
                LocalMusicTopBar(
                    songCount = displaySongs.size,
                    totalCount = localSongs.size,
                    isScanning = isScanning,
                    isBatchMode = isBatchMode,
                    selectedCount = selectedSongs.size,
                    onBack = { navigator.pop() },
                    onRefresh = {
                        coroutineScope.launch {
                            isScanning = true
                            repository.scanMusic()
                            isScanning = false
                        }
                    },
                    onToggleSort = { showSortMenu = true },
                    onToggleBatchMode = { 
                        isBatchMode = !isBatchMode
                        selectedSongs.clear()
                    },
                    onSelectAll = {
                        if (selectedSongs.size == displaySongs.size) {
                            selectedSongs.clear()
                        } else {
                            selectedSongs.clear()
                            selectedSongs.addAll(displaySongs)
                        }
                    },
                    onPlaySelected = {
                        if (selectedSongs.isNotEmpty()) {
                            val queue = selectedSongs.map { it.toSong() }
                            queueManager.playQueue(queue, 0)
                            isBatchMode = false
                            selectedSongs.clear()
                        }
                    }
                )
                
                // 扫描进度条
                AnimatedVisibility(
                    visible = isScanning,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    ScanningProgressBar(
                        progress = scanProgress,
                        message = scanMessage
                    )
                }
                
                // 文件夹筛选器
                if (folders.size > 1) {
                    FolderFilterBar(
                        folders = folders,
                        selectedFolder = selectedFolder,
                        onFolderSelected = { selectedFolder = it }
                    )
                }
                
                // 内容区域
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        !hasPermission -> PermissionRequestPanel(
                            onRequestPermission = {
                                coroutineScope.launch {
                                    hasPermission = repository.requestPermission()
                                    if (hasPermission) {
                                        isScanning = true
                                        repository.scanMusic()
                                        isScanning = false
                                    }
                                }
                            }
                        )
                        
                        localSongs.isEmpty() && !isScanning -> EmptyLocalMusicPanel(
                            onScan = {
                                coroutineScope.launch {
                                    isScanning = true
                                    repository.scanMusic()
                                    isScanning = false
                                }
                            }
                        )
                        
                        displaySongs.isEmpty() -> EmptyFilterResultPanel(
                            onClearFilter = { selectedFolder = null }
                        )
                        
                        else -> LocalMusicList(
                            songs = displaySongs,
                            isBatchMode = isBatchMode,
                            selectedSongs = selectedSongs,
                            onSongClick = { song ->
                                if (isBatchMode) {
                                    if (song in selectedSongs) {
                                        selectedSongs.remove(song)
                                    } else {
                                        selectedSongs.add(song)
                                    }
                                } else {
                                    val queue = displaySongs.map { it.toSong() }
                                    val index = queue.indexOfFirst { it.id == song.id }
                                    queueManager.playQueue(queue, index.coerceAtLeast(0))
                                }
                            },
                            onAddToPlaylist = { showAddToPlaylist = it.toSong() },
                            onPlayNext = { queueManager.addToQueueNext(it.toSong()) }
                        )
                    }
                }
                
                // 批量操作栏
                AnimatedVisibility(
                    visible = isBatchMode && selectedSongs.isNotEmpty(),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    BatchActionBar(
                        selectedCount = selectedSongs.size,
                        onAddToPlaylist = {
                            // TODO: 批量添加到歌单
                        },
                        onPlayNext = {
                            selectedSongs.forEach { queueManager.addToQueueNext(it.toSong()) }
                            isBatchMode = false
                            selectedSongs.clear()
                        },
                        onClear = {
                            selectedSongs.clear()
                        }
                    )
                }
            }
            
            // 排序菜单
            SortDropdownMenu(
                expanded = showSortMenu,
                currentSort = currentSort,
                onSortSelected = { 
                    currentSort = it
                    showSortMenu = false
                },
                onDismiss = { showSortMenu = false }
            )
            
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

/**
 * 排序类型
 */
enum class SortType(val label: String) {
    NAME_ASC("名称 ↑"),
    NAME_DESC("名称 ↓"),
    ARTIST_ASC("艺术家"),
    DURATION_DESC("时长"),
    DATE_ADDED_DESC("最近添加")
}

/**
 * 顶部栏
 */
@Composable
private fun LocalMusicTopBar(
    songCount: Int,
    totalCount: Int,
    isScanning: Boolean,
    isBatchMode: Boolean,
    selectedCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleSort: () -> Unit,
    onToggleBatchMode: () -> Unit,
    onSelectAll: () -> Unit,
    onPlaySelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：返回 + 标题
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassButton(
                onClick = onBack,
                icon = Icons.Default.ArrowBack,
                contentDescription = "返回"
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = if (isBatchMode) "已选择 $selectedCount 首" else "本地音乐",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (!isBatchMode) {
                    Text(
                        text = "$songCount / $totalCount 首",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // 右侧操作按钮
        Row {
            if (isBatchMode) {
                // 批量模式按钮
                GlassButton(
                    onClick = onSelectAll,
                    icon = Icons.Default.SelectAll,
                    contentDescription = "全选"
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                GlassButton(
                    onClick = onPlaySelected,
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "播放选中",
                    tint = Color(0xFF00D4FF)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                GlassButton(
                    onClick = onToggleBatchMode,
                    icon = Icons.Default.Close,
                    contentDescription = "退出批量"
                )
            } else {
                // 正常模式按钮
                GlassButton(
                    onClick = onRefresh,
                    icon = Icons.Default.Refresh,
                    contentDescription = "刷新",
                    tint = if (isScanning) Color(0xFF00D4FF) else Color.White
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                GlassButton(
                    onClick = onToggleSort,
                    icon = Icons.Default.Sort,
                    contentDescription = "排序"
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                GlassButton(
                    onClick = onToggleBatchMode,
                    icon = Icons.Default.CheckCircle,
                    contentDescription = "批量选择"
                )
            }
        }
    }
}

/**
 * 扫描进度条
 */
@Composable
private fun ScanningProgressBar(
    progress: Float,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF00D4FF),
            trackColor = Color.White.copy(alpha = 0.1f)
        )
        
        if (message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * 文件夹筛选栏
 */
@Composable
private fun FolderFilterBar(
    folders: List<String>,
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "全部"按钮
        FilterChip(
            selected = selectedFolder == null,
            onClick = { onFolderSelected(null) },
            label = { Text("全部") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = Color(0xFF00D4FF).copy(alpha = 0.3f),
                selectedLabelColor = Color(0xFF00D4FF)
            )
        )
        
        // 文件夹按钮（限制显示数量）
        folders.take(3).forEach { folder ->
            val folderName = folder.substringAfterLast("/").take(15)
            FilterChip(
                selected = selectedFolder == folder,
                onClick = { onFolderSelected(folder) },
                label = { Text(folderName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF00D4FF).copy(alpha = 0.3f),
                    selectedLabelColor = Color(0xFF00D4FF)
                )
            )
        }
        
        if (folders.size > 3) {
            Text(
                text = "+${folders.size - 3}",
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * 本地音乐列表
 */
@Composable
private fun LocalMusicList(
    songs: List<LocalSong>,
    isBatchMode: Boolean,
    selectedSongs: List<LocalSong>,
    onSongClick: (LocalSong) -> Unit,
    onAddToPlaylist: (LocalSong) -> Unit,
    onPlayNext: (LocalSong) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = songs,
            key = { it.id }
        ) { song ->
            LocalMusicItem(
                song = song,
                isBatchMode = isBatchMode,
                isSelected = song in selectedSongs,
                onClick = { onSongClick(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onPlayNext = { onPlayNext(song) }
            )
        }
    }
}

/**
 * 本地音乐列表项 - 玻璃拟态风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalMusicItem(
    song: LocalSong,
    isBatchMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val backgroundColor = when {
        isSelected -> Color(0xFF00D4FF).copy(alpha = 0.2f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    
    val borderColor = when {
        isSelected -> Color(0xFF00D4FF).copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 批量选择框或封面
            if (isBatchMode) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            } else {
                // 封面
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1A1A2F)),
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
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "${song.artist} · ${song.album}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 时长
            Text(
                text = formatDuration(song.duration),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            // 更多菜单（非批量模式）
            if (!isBatchMode) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = Color(0xFF1A1A2F)
                    ) {
                        DropdownMenuItem(
                            text = { Text("添加到歌单", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.PlaylistAdd,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("下一首播放", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onPlayNext()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 批量操作栏
 */
@Composable
private fun BatchActionBar(
    selectedCount: Int,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2F))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BatchActionButton(
            icon = Icons.Default.PlaylistAdd,
            label = "添加到歌单",
            onClick = onAddToPlaylist
        )
        
        BatchActionButton(
            icon = Icons.Default.QueueMusic,
            label = "下一首播放",
            onClick = onPlayNext
        )
        
        BatchActionButton(
            icon = Icons.Default.Delete,
            label = "清除选择",
            onClick = onClear
        )
    }
}

@Composable
private fun BatchActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF00D4FF),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

/**
 * 排序下拉菜单
 */
@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentSort: SortType,
    onSortSelected: (SortType) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2F)
    ) {
        SortType.entries.forEach { sortType ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = sortType.label,
                        color = if (sortType == currentSort) Color(0xFF00D4FF) else Color.White
                    )
                },
                onClick = { onSortSelected(sortType) },
                trailingIcon = {
                    if (sortType == currentSort) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF00D4FF)
                        )
                    }
                }
            )
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
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF00D4FF).copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "需要存储权限",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "请授予访问本地音乐文件的权限",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00D4FF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("授予权限", fontWeight = FontWeight.Bold)
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
            modifier = Modifier.size(80.dp),
            tint = Color(0xFF00D4FF).copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "暂无本地音乐",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "点击扫描按钮搜索设备中的音乐文件",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onScan,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00D4FF)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("扫描音乐", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 筛选结果为空面板
 */
@Composable
private fun EmptyFilterResultPanel(
    onClearFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "该文件夹暂无音乐",
            color = Color.White,
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(onClick = onClearFilter) {
            Text("清除筛选", color = Color(0xFF00D4FF))
        }
    }
}

/**
 * 玻璃按钮
 */
@Composable
private fun GlassButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = Color.White
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
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
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

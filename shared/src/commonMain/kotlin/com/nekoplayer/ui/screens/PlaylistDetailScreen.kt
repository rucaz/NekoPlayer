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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.PlaylistRepository
import com.nekoplayer.database.Playlist
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 歌单详情页
 */
class PlaylistDetailScreen(private val playlistId: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val playlistRepository: PlaylistRepository = koinInject()
        val queueManager: QueueManager = koinInject()
        val scope = rememberCoroutineScope()

        var playlist by remember { mutableStateOf<Playlist?>(null) }
        var songs by remember { mutableStateOf<List<Pair<com.nekoplayer.database.PlaylistSong, Song>>>(emptyList()) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        // 加载歌单数据
        LaunchedEffect(playlistId) {
            playlist = playlistRepository.getPlaylistById(playlistId)
        }

        // 加载歌曲列表
        LaunchedEffect(playlistId) {
            playlistRepository.getSongsInPlaylist(playlistId).collect { songList ->
                songs = songList
            }
        }

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
                    playlistName = playlist?.name ?: "歌单",
                    onBack = { navigator.pop() },
                    onDeleteClick = { showDeleteConfirm = true }
                )

                // 歌单信息头部
                playlist?.let { pl ->
                    PlaylistHeader(
                        playlist = pl,
                        songCount = songs.size,
                        onPlayAll = {
                            if (songs.isNotEmpty()) {
                                queueManager.playQueue(songs.map { it.second }, 0)
                                navigator.push(NowPlayingScreen(songs.first().second))
                            }
                        }
                    )
                }

                if (songs.isEmpty()) {
                    // 空状态
                    EmptyState()
                } else {
                    // 歌曲列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(songs, key = { it.first.id }) { (playlistSong, song) ->
                            PlaylistSongItem(
                                song = song,
                                onClick = {
                                    // 播放这首歌，并设置队列
                                    val index = songs.indexOfFirst { it.second.id == song.id }
                                    queueManager.playQueue(songs.map { it.second }, index)
                                    navigator.push(NowPlayingScreen(song))
                                },
                                onRemove = {
                                    scope.launch {
                                        playlistRepository.removeSongFromPlaylist(playlistSong.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 删除确认弹窗
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除歌单", color = Color.White) },
                text = { Text("确定要删除这个歌单吗？歌单内的歌曲也将被移除。", color = Color.White.copy(alpha = 0.8f)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                playlistRepository.deletePlaylist(playlistId)
                                showDeleteConfirm = false
                                navigator.pop()
                            }
                        }
                    ) {
                        Text("删除", color = Color(0xFFFF4444))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消", color = Color.White.copy(alpha = 0.6f))
                    }
                },
                containerColor = Color(0xFF1A1A2F)
            )
        }
    }
}

@Composable
private fun TopBar(
    playlistName: String,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit
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
            text = playlistName,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        )

        IconButton(onClick = onDeleteClick) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除歌单",
                tint = Color(0xFFFF4444)
            )
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: Playlist,
    songCount: Int,
    onPlayAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                            .size(48.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$songCount 首歌曲",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 播放全部按钮
                Button(
                    onClick = onPlayAll,
                    enabled = songCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D4FF),
                        disabledContainerColor = Color(0xFF00D4FF).copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("播放全部")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Divider(color = Color.White.copy(alpha = 0.1f))
    }
}

@Composable
private fun PlaylistSongItem(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2F)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A3F))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 更多按钮
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF2A2A3F))
                ) {
                    DropdownMenuItem(
                        text = { Text("从歌单移除", color = Color.White) },
                        onClick = {
                            onRemove()
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF4444)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.MusicOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "歌单为空",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "快去搜索页面添加喜欢的歌曲吧",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}

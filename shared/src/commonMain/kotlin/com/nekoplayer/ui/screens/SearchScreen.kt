package com.nekoplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.MusicSource
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.components.AddToPlaylistDialog
import com.nekoplayer.ui.components.SongActionSheet
import com.nekoplayer.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 搜索界面 - Voyager Screen
 * 支持长按弹出操作菜单：添加到歌单、下一首播放
 */
class SearchScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel: SearchViewModel = koinInject()
        val queueManager: QueueManager = koinInject()
        val scope = rememberCoroutineScope()

        val searchQuery by viewModel.searchQuery.collectAsState()
        val searchResults by viewModel.searchResults.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()

        // 长按菜单状态
        var selectedSong by remember { mutableStateOf<Song?>(null) }
        var showActionSheet by remember { mutableStateOf(false) }
        var showAddToPlaylist by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部栏 - 添加歌单入口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NekoPlayer",
                        color = Color(0xFF00D4FF),
                        fontSize = 20.sp
                    )

                    IconButton(
                        onClick = { navigator.push(PlaylistListScreen()) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "我的歌单",
                            tint = Color(0xFF00D4FF)
                        )
                    }
                    
                    IconButton(
                        onClick = { navigator.push(StatsScreen()) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "统计",
                            tint = Color(0xFF00D4FF)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 搜索栏
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearch = viewModel::search,
                    isLoading = isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 错误提示
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFFF3333),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }

                // 搜索结果列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { song ->
                        SongItem(
                            song = song,
                            onClick = {
                                scope.launch {
                                    // 获取当前点击歌曲的播放链接
                                    val songWithUrl = viewModel.getPlayUrl(song)
                                    if (songWithUrl != null) {
                                        // 只将当前歌曲加入队列（避免队列中有无效歌曲）
                                        queueManager.playQueue(listOf(songWithUrl), 0)
                                        // 跳转到播放界面
                                        navigator.push(NowPlayingScreen(songWithUrl))
                                    }
                                }
                            },
                            onLongClick = {
                                selectedSong = song
                                showActionSheet = true
                            }
                        )
                    }
                }
            }
        }

        // 长按操作菜单
        if (showActionSheet && selectedSong != null) {
            SongActionSheet(
                song = selectedSong!!,
                onDismiss = {
                    showActionSheet = false
                    selectedSong = null
                },
                onAddToPlaylist = {
                    showActionSheet = false
                    // 注意：不清空 selectedSong，因为 AddToPlaylistDialog 需要它
                    showAddToPlaylist = true
                },
                onPlayNext = {
                    scope.launch {
                        val songWithUrl = viewModel.getPlayUrl(selectedSong!!)
                        if (songWithUrl != null) {
                            val nextSong = queueManager.playNextImmediately(songWithUrl)
                            // 如果当前没有在播放，需要跳转到播放页
                            if (queueManager.currentIndex.value == 0) {
                                navigator.push(NowPlayingScreen(nextSong))
                            }
                        }
                        showActionSheet = false
                        selectedSong = null
                    }
                }
            )
        }

        // 添加到歌单弹窗
        if (showAddToPlaylist && selectedSong != null) {
            AddToPlaylistDialog(
                song = selectedSong!!,
                onDismiss = {
                    showAddToPlaylist = false
                    selectedSong = null
                }
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    isLoading: Boolean
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "搜索歌曲、歌手...",
                color = Color.White.copy(alpha = 0.5f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = Color(0xFF00D4FF)
            )
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF00D4FF)
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF1A1A2F),
            unfocusedContainerColor = Color(0xFF1A1A2F),
            focusedBorderColor = Color(0xFF00D4FF),
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            cursorColor = Color(0xFF00D4FF)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun SongItem(
    song: Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 来源标记 + 歌手
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 来源标签
                    SourceTag(source = song.source)

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 播放按钮
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceTag(source: MusicSource) {
    val (text, color) = when (source) {
        MusicSource.BILIBILI -> "bilibili" to Color(0xFFFF69B4)  // 粉红色
        MusicSource.MIGU -> "migu" to Color(0xFF00D4FF)          // 粉蓝色
        MusicSource.LOCAL -> "本地" to Color(0xFF4CAF50)         // 绿色
    }

    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

package com.nekoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.PlaylistRepository
import com.nekoplayer.database.Playlist
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Log tag for debugging
 */
private const val TAG = "NekoPlaylist"

/**
 * 添加到歌单选择器
 */
@Composable
fun AddToPlaylistDialog(
    song: Song,
    onDismiss: () -> Unit
) {
    val playlistRepository: PlaylistRepository = koinInject()
    val scope = rememberCoroutineScope()

    println("[$TAG] Dialog opened for song: ${song.id} - ${song.title}")

    val playlists by playlistRepository.getAllPlaylists().collectAsState(initial = emptyList())
    var isLoading by remember { mutableStateOf(true) }
    var showCreateNew by remember { mutableStateOf(false) }
    var addingPlaylistId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var debugInfo by remember { mutableStateOf("等待点击...") }

    LaunchedEffect(playlists) {
        println("[$TAG] Loaded ${playlists.size} playlists")
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2F)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题
                Text(
                    text = "添加到歌单",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )

                // 调试信息（临时）
                Text(
                    text = "DEBUG: $debugInfo",
                    color = Color.Yellow,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 错误消息
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                if (showCreateNew) {
                    // 新建歌单输入
                    CreatePlaylistInline(
                        onCreate = { name ->
                            scope.launch {
                                try {
                                    val playlistId = playlistRepository.createPlaylist(name)
                                    // 创建后自动添加歌曲到新歌单
                                    val success = playlistRepository.addSongToPlaylist(playlistId, song)
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        // 添加失败（可能已存在），但仍关闭
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    onDismiss()
                                }
                            }
                        },
                        onCancel = { showCreateNew = false }
                    )
                } else {
                    // 新建歌单按钮
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateNew = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "新建歌单",
                            color = Color(0xFF00D4FF),
                            fontSize = 16.sp
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.1f))
                }

                // 显示加载状态
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00D4FF))
                    }
                } else if (playlists.isEmpty()) {
                    Text(
                        text = "暂无歌单，请新建一个",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                // 歌单列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        var isAlreadyInPlaylist by remember { mutableStateOf(false) }
                        var checkStatus by remember { mutableStateOf("未检查") }

                        // 检查歌曲是否已在歌单中
                        LaunchedEffect(playlist.id, song.id) {
                            println("[$TAG] Checking if song ${song.id} is in playlist ${playlist.id}")
                            checkStatus = "检查中..."
                            try {
                                isAlreadyInPlaylist = playlistRepository.isSongInPlaylist(playlist.id, song.id)
                                checkStatus = "结果: $isAlreadyInPlaylist"
                                println("[$TAG] Check result: isAlreadyInPlaylist = $isAlreadyInPlaylist")
                            } catch (e: Exception) {
                                println("[$TAG] ERROR checking song in playlist: ${e.message}")
                                e.printStackTrace()
                                isAlreadyInPlaylist = false
                                checkStatus = "错误"
                            }
                        }

                        val isAdding = addingPlaylistId == playlist.id

                        // 显示调试信息的列表项
                        Column {
                            PlaylistSelectItem(
                                playlist = playlist,
                                isAlreadyAdded = isAlreadyInPlaylist,
                                isAdding = isAdding,
                                onClick = {
                                    debugInfo = "点击: ${playlist.name}\n检查状态: $checkStatus\nisAlready=$isAlreadyInPlaylist, isAdding=$isAdding"
                                    println("[$TAG] Clicked on playlist: ${playlist.id} - ${playlist.name}")
                                    println("[$TAG] isAlreadyInPlaylist=$isAlreadyInPlaylist, isAdding=$isAdding")
                                    
                                    if (isAlreadyInPlaylist) {
                                        debugInfo = "歌曲已在此歌单中"
                                        return@PlaylistSelectItem
                                    }
                                    if (isAdding) {
                                        debugInfo = "正在添加中，请稍候..."
                                        return@PlaylistSelectItem
                                    }
                                    
                                    addingPlaylistId = playlist.id
                                    scope.launch {
                                        try {
                                            debugInfo = "正在调用 addSongToPlaylist..."
                                            println("[$TAG] Calling addSongToPlaylist...")
                                            val success = playlistRepository.addSongToPlaylist(playlist.id, song)
                                            println("[$TAG] addSongToPlaylist returned: $success")
                                            addingPlaylistId = null
                                            if (success) {
                                                debugInfo = "添加成功!"
                                                println("[$TAG] Success! Dismissing dialog")
                                                onDismiss()
                                            } else {
                                                debugInfo = "返回false，检查歌曲是否已存在"
                                                println("[$TAG] Failed to add song")
                                                errorMessage = "添加失败，歌曲可能已存在"
                                            }
                                        } catch (e: Exception) {
                                            addingPlaylistId = null
                                            debugInfo = "异常: ${e.message?.take(50)}"
                                            println("[$TAG] EXCEPTION: ${e.message}")
                                            e.printStackTrace()
                                            errorMessage = "添加失败: ${e.message}"
                                        }
                                    }
                                }
                            )
                            // 显示检查状态（临时调试）
                            Text(
                                text = "[$checkStatus] 已存在=$isAlreadyInPlaylist",
                                color = Color.Yellow.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                modifier = Modifier.padding(start = 56.dp, bottom = 4.dp)
                            )
                        }
                    }
                }

                // 取消按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistInline(
    onCreate: (String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text("歌单名称", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00D4FF),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(
            onClick = { 
                if (name.isNotBlank()) {
                    onCreate(name)
                }
            },
            enabled = name.isNotBlank()
        ) {
            Text("确认", color = if (name.isNotBlank()) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.3f))
        }

        TextButton(onClick = onCancel) {
            Text("取消", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun PlaylistSelectItem(
    playlist: Playlist,
    isAlreadyAdded: Boolean,
    isAdding: Boolean,
    onClick: () -> Unit
) {
    println("[$TAG] PlaylistSelectItem rendered: ${playlist.name}, isAlreadyAdded=$isAlreadyAdded, isAdding=$isAdding, clickableEnabled=${!isAlreadyAdded && !isAdding}")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAlreadyAdded && !isAdding, onClick = {
                println("[$TAG] clickable onClick triggered for ${playlist.name}")
                onClick()
            })
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2A2A3F)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 歌单信息
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = playlist.name,
                color = if (isAlreadyAdded) Color.White.copy(alpha = 0.5f) else Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isAlreadyAdded) {
                Text(
                    text = "已添加",
                    color = Color(0xFF00D4FF).copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        // 状态图标
        when {
            isAdding -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF00D4FF),
                    strokeWidth = 2.dp
                )
            }
            isAlreadyAdded -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 长按歌曲弹出的操作菜单
 */
@Composable
fun SongActionSheet(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2F)
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 歌曲信息头部
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = song.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = song.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                // 操作选项
                ActionItem(
                    icon = Icons.Default.PlaylistPlay,
                    text = "添加到歌单",
                    onClick = onAddToPlaylist
                )

                ActionItem(
                    icon = Icons.Default.SkipNext,
                    text = "下一首播放",
                    onClick = onPlayNext
                )

                Divider(color = Color.White.copy(alpha = 0.1f))

                // 取消按钮
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("取消", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

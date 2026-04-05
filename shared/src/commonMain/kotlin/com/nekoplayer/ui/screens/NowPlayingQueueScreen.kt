package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import com.nekoplayer.player.QueueManager
import org.koin.compose.koinInject

/**
 * 当前播放队列页面
 * 显示播放队列，支持切歌、删除、清空
 */
class NowPlayingQueueScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val queueManager: QueueManager = koinInject()
        val player: AudioPlayer = koinInject()

        val queue by queueManager.currentQueue.collectAsState()
        val currentIndex by queueManager.currentIndex.collectAsState()
        val currentSong by queueManager.currentSong.collectAsState()
        val playMode by queueManager.playMode.collectAsState()

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
                    songCount = queue.size,
                    playMode = playMode,
                    onBack = { navigator.pop() },
                    onClear = { queueManager.clearQueue() },
                    onToggleMode = { queueManager.togglePlayMode() }
                )

                if (queue.isEmpty()) {
                    EmptyState()
                } else {
                    // 队列列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue, key = { _, song -> song.id }) { index, song ->
                            QueueSongItem(
                                song = song,
                                isPlaying = index == currentIndex,
                                index = index + 1,
                                onClick = {
                                    queueManager.skipToSong(index)
                                    player.prepare(song)
                                    player.play()
                                },
                                onRemove = {
                                    queueManager.removeFromQueue(index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    songCount: Int,
    playMode: QueueManager.PlayMode,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onToggleMode: () -> Unit
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

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "播放队列",
                color = Color.White,
                fontSize = 18.sp
            )
            Text(
                text = "$songCount 首歌曲",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }

        Row {
            // 播放模式切换
            IconButton(onClick = onToggleMode) {
                val (icon, desc) = when (playMode) {
                    QueueManager.PlayMode.SEQUENTIAL -> Icons.Default.Repeat to "顺序播放"
                    QueueManager.PlayMode.SHUFFLE -> Icons.Default.Shuffle to "随机播放"
                    QueueManager.PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne to "单曲循环"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = Color(0xFF00D4FF)
                )
            }

            // 清空队列
            if (songCount > 0) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "清空队列",
                        tint = Color(0xFFFF4444)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueSongItem(
    song: Song,
    isPlaying: Boolean,
    index: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val bgColor = if (isPlaying) Color(0xFF00D4FF).copy(alpha = 0.15f) else Color(0xFF1A1A2F)
    val borderColor = if (isPlaying) Color(0xFF00D4FF) else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isPlaying) 1.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 序号或播放指示器
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    PlayingIndicator()
                } else {
                    Text(
                        text = index.toString(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

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
                    color = if (isPlaying) Color(0xFF00D4FF) else Color.White,
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

            // 删除按钮
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        repeat(3) { index ->
            val animation = rememberInfiniteTransition(label = "playing$index")
            val height by animation.animateFloat(
                initialValue = 4f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + index * 100),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$index"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF00D4FF))
            )
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
            imageVector = Icons.Default.QueueMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "队列为空",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "快去搜索页面添加歌曲吧",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}
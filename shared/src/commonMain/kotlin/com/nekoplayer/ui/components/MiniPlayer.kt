package com.nekoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.Navigator
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import com.nekoplayer.player.QueueManager
import com.nekoplayer.ui.screens.NowPlayingScreen
import org.koin.compose.koinInject

/**
 * 底部迷你播放器条
 * 参考网易云风格设计
 */
@Composable
fun MiniPlayer(
    navigator: Navigator,
    modifier: Modifier = Modifier
) {
    val player: AudioPlayer = koinInject()
    val queueManager: QueueManager = koinInject()

    val playerState by player.playerState.collectAsState()
    val currentPosition by player.currentPosition.collectAsState()
    val duration by player.duration.collectAsState()
    val currentSong by queueManager.currentSong.collectAsState()

    // 只有在有歌曲时才显示
    val isVisible = currentSong != null

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier
    ) {
        currentSong?.let { song ->
            MiniPlayerContent(
                song = song,
                playerState = playerState,
                currentPosition = currentPosition,
                duration = duration,
                onClick = {
                    // 点击展开全屏播放页
                    navigator.push(NowPlayingScreen(song))
                },
                onPlayPauseClick = {
                    when (playerState) {
                        is PlayerState.Playing -> player.pause()
                        is PlayerState.Paused, is PlayerState.Idle -> player.play()
                        else -> {}
                    }
                },
                onNextClick = {
                    player.playNext()
                }
            )
        }
    }
}

@Composable
private fun MiniPlayerContent(
    song: Song,
    playerState: PlayerState,
    currentPosition: Long,
    duration: Long,
    onClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit
) {
    val isPlaying = playerState is PlayerState.Playing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2F))
            .clickable(onClick = onClick)
    ) {
        // 进度条（细线）
        val progress = if (duration > 0) {
            currentPosition.toFloat() / duration.toFloat()
        } else 0f

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = Color(0xFF00D4FF),
            trackColor = Color.White.copy(alpha = 0.1f),
        )

        // 主内容区域（高度 62dp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面（48dp）
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
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 播放控制按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 播放/暂停按钮
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 下一首按钮
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

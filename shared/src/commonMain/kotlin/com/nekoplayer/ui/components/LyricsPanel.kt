package com.nekoplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nekoplayer.lyrics.LyricLine
import com.nekoplayer.lyrics.Lyrics
import kotlinx.coroutines.launch

/**
 * 歌词面板组件
 * 
 * @param lyrics 歌词数据
 * @param currentTimeMs 当前播放时间（毫秒）
 * @param isPlaying 是否正在播放
 * @param onLineClick 点击歌词行回调，参数为时间戳
 * @param modifier 修饰符
 */
@Composable
fun LyricsPanel(
    lyrics: Lyrics,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onLineClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (lyrics.lines.isEmpty()) {
        EmptyLyrics(modifier = modifier)
        return
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isUserScrolling by remember { mutableStateOf(false) }
    var userScrollJob by remember { mutableStateOf< kotlinx.coroutines.Job?>(null) }
    
    // 计算当前高亮的行索引
    val currentLineIndex = remember(currentTimeMs, lyrics) {
        lyrics.getCurrentLineIndex(currentTimeMs)
    }
    
    // 自动滚动到当前歌词行
    LaunchedEffect(currentLineIndex, isPlaying) {
        if (isPlaying && !isUserScrolling && currentLineIndex >= 0) {
            listState.animateScrollToItem(
                index = currentLineIndex,
                scrollOffset = -listState.layoutInfo.viewportSize.height / 3
            )
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 120.dp) // 上下留白，使当前行居中
    ) {
        itemsIndexed(
            items = lyrics.lines,
            key = { index, line -> "${line.timeMs}_$index" }
        ) { index, line ->
            val isCurrentLine = index == currentLineIndex
            val isPastLine = index < currentLineIndex
            
            LyricLineItem(
                line = line,
                isCurrentLine = isCurrentLine,
                isPastLine = isPastLine,
                onClick = {
                    onLineClick(line.timeMs)
                    // 用户点击后，短暂停止自动滚动
                    isUserScrolling = true
                    userScrollJob?.cancel()
                    userScrollJob = coroutineScope.launch {
                        kotlinx.coroutines.delay(3000) // 3秒后恢复自动滚动
                        isUserScrolling = false
                    }
                }
            )
        }
    }
}

/**
 * 单行歌词项
 */
@Composable
private fun LyricLineItem(
    line: LyricLine,
    isCurrentLine: Boolean,
    isPastLine: Boolean,
    onClick: () -> Unit
) {
    // 动画效果
    val targetAlpha = when {
        isCurrentLine -> 1f
        isPastLine -> 0.4f
        else -> 0.6f // 未来的歌词
    }
    
    val targetScale = if (isCurrentLine) 1.05f else 1f
    val targetFontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal
    
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    
    val contentColor = if (isCurrentLine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .scale(scale)
            .alpha(alpha)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主歌词
        Text(
            text = line.content,
            fontSize = if (isCurrentLine) 18.sp else 16.sp,
            fontWeight = targetFontWeight,
            color = contentColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        // 翻译（如果有）
        line.translation?.let { trans ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = trans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 空歌词状态
 */
@Composable
private fun EmptyLyrics(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无歌词",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击搜索歌词",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 歌词面板预览状态（简化版，用于缩略图展示）
 */
@Composable
fun LyricsPanelPreview(
    lyrics: Lyrics,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
    ) {
        if (lyrics.lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "歌词",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            // 显示前3行作为预览
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                lyrics.lines.take(3).forEach { line ->
                    Text(
                        text = line.content,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

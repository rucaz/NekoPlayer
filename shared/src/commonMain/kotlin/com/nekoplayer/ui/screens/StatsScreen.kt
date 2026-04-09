package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import com.nekoplayer.data.model.Song
import com.nekoplayer.data.repository.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 音乐统计页面
 * 
 * 展示用户的音乐收听统计：
 * - 总体统计（总播放次数、总时长、活跃天数）
 * - 本周/本月统计
 * - 来源分布（B站/咪咕/本地）
 * - 24小时收听分布图表
 * - Top艺术家排行
 * - Top歌曲排行
 */
class StatsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val statsRepository: StatsRepository = koinInject()
        val coroutineScope = rememberCoroutineScope()

        // 状态
        var overallStats by remember { mutableStateOf<OverallStats?>(null) }
        var weekStats by remember { mutableStateOf<WeekMonthStats?>(null) }
        var monthStats by remember { mutableStateOf<WeekMonthStats?>(null) }
        var topArtists by remember { mutableStateOf<List<ArtistStats>>(emptyList()) }
        var topSongs by remember { mutableStateOf<List<SongStats>>(emptyList()) }
        var hourlyDistribution by remember { mutableStateOf(List(24) { 0 }) }
        var sourceBreakdown by remember { mutableStateOf(SourceBreakdown(0, 0, 0)) }
        var isLoading by remember { mutableStateOf(true) }
        var selectedTab by remember { mutableStateOf(StatsTab.OVERVIEW) }

        // 加载数据
        LaunchedEffect(Unit) {
            isLoading = true
            
            // 并行加载所有数据
            coroutineScope.launch {
                overallStats = statsRepository.getOverallStats().firstOrNull()
            }
            coroutineScope.launch {
                weekStats = statsRepository.getThisWeekStats().firstOrNull()
            }
            coroutineScope.launch {
                monthStats = statsRepository.getThisMonthStats().firstOrNull()
            }
            coroutineScope.launch {
                topArtists = statsRepository.getTopArtistsByPlays(10).firstOrNull() ?: emptyList()
            }
            coroutineScope.launch {
                topSongs = statsRepository.getTopSongs(10).firstOrNull() ?: emptyList()
            }
            coroutineScope.launch {
                hourlyDistribution = statsRepository.getHourlyDistribution(30)
            }
            coroutineScope.launch {
                // 获取最近30天的来源分布
                val recentStats = statsRepository.getRecentDailyStats(30).firstOrNull() ?: emptyList()
                var bilibili = 0
                var migu = 0
                var local = 0
                recentStats.forEach {
                    bilibili += it.sourceBreakdown.bilibili
                    migu += it.sourceBreakdown.migu
                    local += it.sourceBreakdown.local
                }
                sourceBreakdown = SourceBreakdown(bilibili, migu, local)
            }
            
            isLoading = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            // 背景渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF00D4FF).copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                StatsTopBar(
                    onBack = { navigator.pop() }
                )

                // 标签页切换
                TabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                // 内容区域
                when (selectedTab) {
                    StatsTab.OVERVIEW -> OverviewContent(
                        overallStats = overallStats,
                        weekStats = weekStats,
                        monthStats = monthStats,
                        sourceBreakdown = sourceBreakdown,
                        hourlyDistribution = hourlyDistribution,
                        isLoading = isLoading
                    )
                    StatsTab.ARTISTS -> ArtistsContent(
                        artists = topArtists,
                        isLoading = isLoading,
                        onArtistClick = { /* TODO: 跳转到艺术家详情 */ }
                    )
                    StatsTab.SONGS -> SongsContent(
                        songs = topSongs,
                        isLoading = isLoading,
                        onSongClick = { song ->
                            navigator.push(SongDetailScreen(song.song))
                        }
                    )
                }
            }
        }
    }
}

/**
 * 统计标签页
 */
private enum class StatsTab {
    OVERVIEW,   // 概览
    ARTISTS,    // 艺术家
    SONGS       // 歌曲
}

/**
 * 顶部栏
 */
@Composable
private fun StatsTopBar(
    onBack: () -> Unit
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
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White
            )
        }

        Text(
            text = "音乐统计",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )

        // 占位保持对称
        Box(modifier = Modifier.size(48.dp))
    }
}

/**
 * 标签页切换
 */
@Composable
private fun TabRow(
    selectedTab: StatsTab,
    onTabSelected: (StatsTab) -> Unit
) {
    val tabs = listOf(
        StatsTab.OVERVIEW to "概览",
        StatsTab.ARTISTS to "艺术家",
        StatsTab.SONGS to "歌曲"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(4.dp)
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) Color(0xFF00D4FF).copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color(0xFF00D4FF) else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

// ==================== 概览内容 ====================

@Composable
private fun OverviewContent(
    overallStats: OverallStats?,
    weekStats: WeekMonthStats?,
    monthStats: WeekMonthStats?,
    sourceBreakdown: SourceBreakdown,
    hourlyDistribution: List<Int>,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // 总体统计卡片
        OverallStatsCard(
            stats = overallStats,
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 本周/本月统计
        WeekMonthStatsRow(
            weekStats = weekStats,
            monthStats = monthStats,
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 来源分布
        SourceDistributionCard(
            breakdown = sourceBreakdown,
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 24小时收听分布
        HourlyDistributionCard(
            hourlyData = hourlyDistribution,
            isLoading = isLoading
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * 总体统计卡片
 */
@Composable
private fun OverallStatsCard(
    stats: OverallStats?,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "听歌概览",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                LoadingPlaceholder()
            } else if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BigStatItem(
                        value = formatBigNumber(stats.totalPlays),
                        label = "总播放次数"
                    )
                    BigStatItem(
                        value = formatDuration(stats.totalDuration),
                        label = "总播放时长"
                    )
                    BigStatItem(
                        value = "${stats.activeDays}",
                        label = "活跃天数"
                    )
                }
            } else {
                EmptyStatsPlaceholder("暂无统计数据，快去听歌吧！")
            }
        }
    }
}

/**
 * 大数字统计项
 */
@Composable
private fun BigStatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            color = Color(0xFF00D4FF),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}

/**
 * 本周/本月统计行
 */
@Composable
private fun WeekMonthStatsRow(
    weekStats: WeekMonthStats?,
    monthStats: WeekMonthStats?,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WeekMonthCard(
            title = "本周",
            stats = weekStats,
            isLoading = isLoading,
            modifier = Modifier.weight(1f)
        )
        WeekMonthCard(
            title = "本月",
            stats = monthStats,
            isLoading = isLoading,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 本周/本月统计卡片
 */
@Composable
private fun WeekMonthCard(
    title: String,
    stats: WeekMonthStats?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                SmallLoadingPlaceholder()
            } else if (stats != null) {
                Text(
                    text = "${stats.totalPlays}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "次播放",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${stats.uniqueSongs} 首歌曲",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                Text(
                    text = formatDuration(stats.totalDuration),
                    color = Color(0xFF00D4FF),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    text = "-",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 24.sp
                )
            }
        }
    }
}

/**
 * 来源分布卡片
 */
@Composable
private fun SourceDistributionCard(
    breakdown: SourceBreakdown,
    isLoading: Boolean
) {
    val total = breakdown.bilibili + breakdown.migu + breakdown.local

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "听歌来源分布",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                LoadingPlaceholder()
            } else if (total > 0) {
                // 饼图
                SourcePieChart(
                    bilibili = breakdown.bilibili,
                    migu = breakdown.migu,
                    local = breakdown.local,
                    total = total
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 图例
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    SourceLegendItem(
                        color = Color(0xFFFB7299),
                        label = "B站",
                        count = breakdown.bilibili,
                        percentage = if (total > 0) (breakdown.bilibili * 100 / total) else 0
                    )
                    SourceLegendItem(
                        color = Color(0xFF00D4FF),
                        label = "咪咕",
                        count = breakdown.migu,
                        percentage = if (total > 0) (breakdown.migu * 100 / total) else 0
                    )
                    SourceLegendItem(
                        color = Color(0xFF4CAF50),
                        label = "本地",
                        count = breakdown.local,
                        percentage = if (total > 0) (breakdown.local * 100 / total) else 0
                    )
                }
            } else {
                EmptyStatsPlaceholder("暂无来源数据")
            }
        }
    }
}

/**
 * 来源饼图
 */
@Composable
private fun SourcePieChart(
    bilibili: Int,
    migu: Int,
    local: Int,
    total: Int
) {
    val colors = listOf(
        Color(0xFFFB7299),  // B站粉
        Color(0xFF00D4FF),  // 咪咕青
        Color(0xFF4CAF50)   // 本地绿
    )
    val values = listOf(bilibili, migu, local)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            var startAngle = -90f
            values.forEachIndexed { index, value ->
                if (value > 0 && total > 0) {
                    val sweepAngle = (value.toFloat() / total) * 360f
                    drawArc(
                        color = colors[index],
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true
                    )
                    startAngle += sweepAngle
                }
            }

            // 中心镂空形成环形
            drawCircle(
                color = Color(0xFF1A1A2F),
                radius = size.minDimension / 4
            )
        }

        // 中心文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$total",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "总播放",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * 来源图例项
 */
@Composable
private fun SourceLegendItem(
    color: Color,
    label: String,
    count: Int,
    percentage: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        Text(
            text = "$count 次",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "$percentage%",
            color = color,
            fontSize = 10.sp
        )
    }
}

/**
 * 24小时收听分布卡片
 */
@Composable
private fun HourlyDistributionCard(
    hourlyData: List<Int>,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "24小时收听分布",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "你最喜欢在哪个时段听歌？",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (isLoading) {
                LoadingPlaceholder()
            } else if (hourlyData.sum() > 0) {
                HourlyBarChart(hourlyData = hourlyData)

                Spacer(modifier = Modifier.height(12.dp))

                // 时间标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("00:00", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text("06:00", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text("12:00", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text("18:00", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                    Text("23:00", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
                }

                // 找出高峰时段
                val maxHour = hourlyData.indexOf(hourlyData.maxOrNull() ?: 0)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🎵 你的听歌高峰时段是 ${maxHour.toString().padStart(2, '0')}:00",
                    color = Color(0xFF00D4FF),
                    fontSize = 14.sp
                )
            } else {
                EmptyStatsPlaceholder("暂无时段数据")
            }
        }
    }
}

/**
 * 24小时柱状图
 */
@Composable
private fun HourlyBarChart(hourlyData: List<Int>) {
    val maxValue = hourlyData.maxOrNull()?.coerceAtLeast(1) ?: 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = size.width / 24f
            val heightScale = size.height / maxValue

            hourlyData.forEachIndexed { hour, value ->
                if (value > 0) {
                    val barHeight = value * heightScale
                    val x = hour * barWidth
                    val y = size.height - barHeight

                    // 柱子
                    drawRoundRect(
                        color = Color(0xFF00D4FF).copy(alpha = 0.6f),
                        topLeft = Offset(x + 1, y),
                        size = Size(barWidth - 2, barHeight),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                }
            }

            // 基准线
            drawLine(
                color = Color.White.copy(alpha = 0.1f),
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1f
            )
        }
    }
}

// ==================== 艺术家内容 ====================

@Composable
private fun ArtistsContent(
    artists: List<ArtistStats>,
    isLoading: Boolean,
    onArtistClick: (ArtistStats) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00D4FF))
        }
    } else if (artists.isEmpty()) {
        EmptyContent(message = "暂无艺术家数据")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(artists.withIndex().toList()) { (index, artist) ->
                ArtistListItem(
                    rank = index + 1,
                    artist = artist,
                    onClick = { onArtistClick(artist) }
                )
            }
        }
    }
}

/**
 * 艺术家列表项
 */
@Composable
private fun ArtistListItem(
    rank: Int,
    artist: ArtistStats,
    onClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)  // 金色
        2 -> Color(0xFFC0C0C0)  // 银色
        3 -> Color(0xFFCD7F32)  // 铜色
        else -> Color.White.copy(alpha = 0.3f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = rankColor,
                    fontSize = if (rank <= 3) 20.sp else 16.sp,
                    fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 艺术家图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
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
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 艺术家信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.artistName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${artist.totalPlays} 次播放 · ${formatDuration(artist.totalDuration)}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }

            // 箭头
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ==================== 歌曲内容 ====================

@Composable
private fun SongsContent(
    songs: List<SongStats>,
    isLoading: Boolean,
    onSongClick: (SongStats) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF00D4FF))
        }
    } else if (songs.isEmpty()) {
        EmptyContent(message = "暂无歌曲数据")
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(songs.withIndex().toList()) { (index, song) ->
                SongListItem(
                    rank = index + 1,
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

/**
 * 歌曲列表项
 */
@Composable
private fun SongListItem(
    rank: Int,
    song: SongStats,
    onClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.White.copy(alpha = 0.3f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = rankColor,
                    fontSize = if (rank <= 3) 18.sp else 14.sp,
                    fontWeight = if (rank <= 3) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲封面
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A2F))
            ) {
                song.song.coverUrl?.let { coverUrl ->
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: run {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.song.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = song.song.artist,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${song.totalPlays} 次 · ${formatDuration(song.totalDuration)}",
                    color = Color(0xFF00D4FF),
                    fontSize = 11.sp
                )
            }

            // 播放图标
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ==================== 通用组件 ====================

@Composable
private fun EmptyContent(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EmptyStatsPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF00D4FF),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun SmallLoadingPlaceholder() {
    Box(
        modifier = Modifier.padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF00D4FF),
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
    }
}

// ==================== 工具函数 ====================

private fun formatBigNumber(number: Int): String {
    return when {
        number >= 10000 -> "${number / 10000}.${(number % 10000) / 1000}万"
        number >= 1000 -> "${number / 1000},${(number % 1000).toString().padStart(3, '0')}"
        else -> number.toString()
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / 3600000
    val minutes = (durationMs % 3600000) / 60000

    return when {
        hours >= 24 -> "${hours / 24}天${hours % 24}小时"
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟"
        else -> "少于1分钟"
    }
}

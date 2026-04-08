package com.nekoplayer.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.nekoplayer.audio.fingerprint.HumRecognizer
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 哼唱识别页面
 * 
 * 技术验证：端侧实时音频指纹匹配
 */
class HumRecognitionScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val recognizer: HumRecognizer = koinInject()
        val queueManager: QueueManager = koinInject()
        
        val coroutineScope = rememberCoroutineScope()
        
        // 状态
        var isRecording by remember { mutableStateOf(false) }
        var recordingProgress by remember { mutableStateOf(0f) }
        var isRecognizing by remember { mutableStateOf(false) }
        var recognitionResults by remember { mutableStateOf<List<HumRecognizer.RecognitionResult>?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        // 录音动画
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
        
        // 模拟录音进度（实际应连接真实录音器）
        LaunchedEffect(isRecording) {
            if (isRecording) {
                recordingProgress = 0f
                while (isRecording && recordingProgress < 1f) {
                    delay(50)
                    recordingProgress += 0.01f
                }
                if (recordingProgress >= 1f) {
                    // 自动停止并识别
                    isRecording = false
                    isRecognizing = true
                    
                    // 模拟识别（实际应使用真实录音数据）
                    coroutineScope.launch {
                        delay(1500) // 模拟处理时间
                        
                        // 模拟结果
                        recognitionResults = listOf(
                            HumRecognizer.RecognitionResult(
                                songId = "demo_song_1",
                                confidence = 0.92f,
                                matchType = HumRecognizer.MatchType.FINGERPRINT
                            ),
                            HumRecognizer.RecognitionResult(
                                songId = "demo_song_2", 
                                confidence = 0.78f,
                                matchType = HumRecognizer.MatchType.FINGERPRINT
                            ),
                            HumRecognizer.RecognitionResult(
                                songId = "demo_song_3",
                                confidence = 0.65f,
                                matchType = HumRecognizer.MatchType.FINGERPRINT
                            )
                        )
                        isRecognizing = false
                    }
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    
                    Text(
                        text = "哼唱识别",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // 索引状态
                    val stats = recognizer.getIndexStats()
                    Text(
                        text = "${stats.songCount}首",
                        color = Color(0xFF00D4FF),
                        fontSize = 12.sp
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 录音按钮区域
                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 脉冲动画背景
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFF00D4FF).copy(alpha = 0.2f)
                                )
                        )
                    }
                    
                    // 进度环
                    if (isRecording) {
                        CircularProgressIndicator(
                            progress = { recordingProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF00D4FF),
                            strokeWidth = 4.dp,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                    
                    // 主按钮
                    Button(
                        onClick = {
                            if (isRecording) {
                                isRecording = false
                            } else if (!isRecognizing) {
                                isRecording = true
                                recognitionResults = null
                                errorMessage = null
                            }
                        },
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) {
                                Color(0xFFE91E63)
                            } else {
                                Color(0xFF00D4FF)
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) {
                                Icons.Default.Stop
                            } else {
                                Icons.Default.Mic
                            },
                            contentDescription = if (isRecording) "停止" else "录音",
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // 提示文字
                Text(
                    text = when {
                        isRecording -> "正在聆听... ${(recordingProgress * 100).toInt()}%"
                        isRecognizing -> "正在识别..."
                        recognitionResults != null -> "识别完成"
                        else -> "点击麦克风，哼唱一段旋律"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 识别结果
                if (isRecognizing) {
                    CircularProgressIndicator(
                        color = Color(0xFF00D4FF),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                recognitionResults?.let { results ->
                    RecognitionResultsList(
                        results = results,
                        onResultClick = { result ->
                            // 播放识别结果
                            // TODO: 根据songId获取歌曲并播放
                        }
                    )
                }
                
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = Color(0xFFE91E63),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 识别结果列表
 */
@Composable
private fun RecognitionResultsList(
    results: List<HumRecognizer.RecognitionResult>,
    onResultClick: (HumRecognizer.RecognitionResult) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "识别结果",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        results.forEachIndexed { index, result ->
            RecognitionResultItem(
                rank = index + 1,
                result = result,
                onClick = { onResultClick(result) }
            )
            
            if (index < results.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 识别结果项
 */
@Composable
private fun RecognitionResultItem(
    rank: Int,
    result: HumRecognizer.RecognitionResult,
    onClick: () -> Unit
) {
    val confidenceColor = when {
        result.confidence >= 0.8f -> Color(0xFF4CAF50)
        result.confidence >= 0.6f -> Color(0xFFFFC107)
        else -> Color(0xFFFF5722)
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2F)
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
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700).copy(alpha = 0.2f)
                            2 -> Color(0xFFC0C0C0).copy(alpha = 0.2f)
                            3 -> Color(0xFFCD7F32).copy(alpha = 0.2f)
                            else -> Color.White.copy(alpha = 0.1f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$rank",
                    color = when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> Color.White
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 歌曲信息（简化显示ID）
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Song ID: ${result.songId}",
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                
                Text(
                    text = "Match: ${result.matchType.name}",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            
            // 置信度
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${(result.confidence * 100).toInt()}%",
                    color = confidenceColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // 置信度条
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(result.confidence)
                            .background(confidenceColor)
                    )
                }
            }
        }
    }
}

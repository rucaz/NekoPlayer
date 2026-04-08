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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.nekoplayer.audio.fingerprint.HumRecognizer
import com.nekoplayer.audio.recorder.AudioRecorder
import com.nekoplayer.player.QueueManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 哼唱识别页面
 * 
 * 技术验证：端侧实时音频指纹匹配 + 实时录音
 */
class HumRecognitionScreen : Screen {
    
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val recognizer: HumRecognizer = koinInject()
        val recorder: AudioRecorder = koinInject()
        val queueManager: QueueManager = koinInject()
        
        val coroutineScope = rememberCoroutineScope()
        
        // 状态
        val recorderState by recorder.state.collectAsState(AudioRecorder.State.IDLE)
        val durationMs by recorder.durationMs.collectAsState(0L)
        var isRecognizing by remember { mutableStateOf(false) }
        var recognitionResults by remember { mutableStateOf<List<HumRecognizer.RecognitionResult>?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        
        // 累积的音频数据（用于识别）
        var recordedAudio by remember { mutableStateOf<MutableList<Short>>(mutableListOf()) }
        
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
        
        // 收集实时音频流
        LaunchedEffect(Unit) {
            recorder.audioStream.collect { chunk ->
                // 累积音频数据
                recordedAudio.addAll(chunk.data.toList())
            }
        }
        
        // 监听录音状态
        LaunchedEffect(recorderState) {
            when (recorderState) {
                AudioRecorder.State.STOPPED -> {
                    // 录音停止，开始识别
                    if (recordedAudio.isNotEmpty() && !isRecognizing) {
                        isRecognizing = true
                        recognitionResults = null
                        
                        coroutineScope.launch {
                            try {
                                val audioData = recordedAudio.toShortArray()
                                val results = recognizer.recognize(audioData, topK = 5)
                                recognitionResults = results
                            } catch (e: Exception) {
                                errorMessage = "识别失败: ${e.message}"
                            } finally {
                                isRecognizing = false
                                recordedAudio = mutableListOf()
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        
        // 清理资源
        DisposableEffect(Unit) {
            onDispose {
                recorder.release()
            }
        }
        
        val isRecording = recorderState == AudioRecorder.State.RECORDING
        val recordingProgress = (durationMs / 5000f).coerceIn(0f, 1f) // 5秒为100%
        
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
                    IconButton(onClick = { 
                        recorder.release()
                        navigator.pop() 
                    }) {
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
                            when (recorderState) {
                                AudioRecorder.State.IDLE, AudioRecorder.State.STOPPED -> {
                                    // 开始录音
                                    recordedAudio = mutableListOf()
                                    recognitionResults = null
                                    errorMessage = null
                                    
                                    coroutineScope.launch {
                                        val result = recorder.start(
                                            AudioRecorder.Config(
                                                sampleRate = 16000,
                                                bufferSize = 1024
                                            )
                                        )
                                        if (result.isFailure) {
                                            errorMessage = result.exceptionOrNull()?.message 
                                                ?: "录音启动失败"
                                        }
                                    }
                                }
                                AudioRecorder.State.RECORDING -> {
                                    // 停止录音
                                    coroutineScope.launch {
                                        recorder.stop()
                                    }
                                }
                                else -> {}
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
                        ),
                        enabled = !isRecognizing
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
                
                // 录音时长显示
                if (durationMs > 0 && recorderState != AudioRecorder.State.STOPPED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${durationMs / 1000}.${(durationMs % 1000) / 100}s",
                        color = Color(0xFF00D4FF),
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 识别中指示器
                if (isRecognizing) {
                    CircularProgressIndicator(
                        color = Color(0xFF00D4FF),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在分析音频指纹...",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
                
                // 识别结果
                recognitionResults?.let { results ->
                    RecognitionResultsList(
                        results = results,
                        onResultClick = { result ->
                            // TODO: 根据songId获取歌曲并播放
                        }
                    )
                }
                
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE91E63).copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFE91E63),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
            
            // 歌曲信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.songId,
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

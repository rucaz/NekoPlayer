package com.nekoplayer.audio.fingerprint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 哼唱识别器
 * 
 * 将用户的哼唱片段与曲库匹配，找到最相似的歌曲。
 * 基于动态时间规整（DTW）算法处理时序差异。
 */
class HumRecognizer(
    private val fingerprinter: AudioFingerprinter = ChromaprintFingerprinter(),
    private val index: VectorIndex = VectorIndex()
) {
    
    /**
     * 索引歌曲
     * @param songId 歌曲ID
     * @param audioData 歌曲音频数据（PCM）
     */
    suspend fun indexSong(songId: String, audioData: ShortArray) {
        withContext(Dispatchers.Default) {
            val fingerprint = fingerprinter.extractFingerprint(audioData)
            index.add(songId, fingerprint)
        }
    }
    
    /**
     * 识别哼唱片段
     * @param humData 哼唱音频数据（PCM, 16kHz, 单声道）
     * @param topK 返回结果数
     * @return 识别结果列表
     */
    suspend fun recognize(humData: ShortArray, topK: Int = 5): List<RecognitionResult> {
        return withContext(Dispatchers.Default) {
            // 1. 提取哼唱指纹
            val humFingerprint = fingerprinter.extractFingerprint(humData)
            
            // 2. 向量索引搜索候选
            val candidates = index.search(humFingerprint, topK * 3)
            
            // 3. 返回结果（可扩展：对候选做精细DTW匹配）
            candidates.map { result ->
                RecognitionResult(
                    songId = result.id,
                    confidence = (1 - result.distance).coerceIn(0f, 1f),
                    matchType = MatchType.FINGERPRINT
                )
            }.sortedByDescending { it.confidence }
        }
    }
    
    /**
     * 实时识别（流式）
 * 用于边录音边识别，适合3-5秒短片段
     * @param audioChunk 音频块
     * @return 识别结果或null
     */
    suspend fun recognizeStreaming(audioChunk: ShortArray): RecognitionResult? {
        // 简化的流式识别：累积到足够长度后识别
        return withContext(Dispatchers.Default) {
            if (audioChunk.size < AudioFingerprinter.SAMPLE_RATE * 2) {  // 至少2秒
                return@withContext null
            }
            
            val results = recognize(audioChunk, 1)
            results.firstOrNull()?.takeIf { it.confidence > 0.7f }
        }
    }
    
    /**
     * 时序匹配（DTW）
     * 对向量搜索的候选进行精细匹配
     */
    suspend fun temporalMatch(
        humTemporalFingerprint: List<FloatArray>,
        candidateId: String,
        candidateTemporalFingerprint: List<FloatArray>
    ): Float {
        return withContext(Dispatchers.Default) {
            // 简化DTW实现
            val dtw = DynamicTimeWarping()
            val distance = dtw.compute(
                humTemporalFingerprint.toTypedArray(),
                candidateTemporalFingerprint.toTypedArray()
            )
            
            // 转换为相似度分数
            (1 / (1 + distance)).toFloat()
        }
    }
    
    /**
     * 获取索引状态
     */
    fun getIndexStats(): IndexStats {
        return IndexStats(
            songCount = index.size(),
            estimatedMemoryMB = index.estimatedMemoryMB()
        )
    }
    
    /**
     * 清空索引
     */
    fun clearIndex() {
        index.clear()
    }
    
    /**
     * 识别结果
     */
    data class RecognitionResult(
        val songId: String,
        val confidence: Float,  // 0-1，越高越匹配
        val matchType: MatchType
    )
    
    /**
     * 匹配类型
     */
    enum class MatchType {
        FINGERPRINT,    // 指纹匹配
        DTW             // 动态时间规整匹配
    }
    
    /**
     * 索引统计
     */
    data class IndexStats(
        val songCount: Int,
        val estimatedMemoryMB: Float
    )
}

/**
 * 动态时间规整（DTW）算法
 * 用于处理哼唱和原曲的时序差异
 */
class DynamicTimeWarping {
    
    /**
     * 计算两个序列的DTW距离
     */
    fun compute(seqA: Array<FloatArray>, seqB: Array<FloatArray>): Double {
        val n = seqA.size
        val m = seqB.size
        
        // 距离矩阵
        val distMatrix = Array(n) { i ->
            DoubleArray(m) { j ->
                euclideanDistance(seqA[i], seqB[j])
            }
        }
        
        // DTW累积矩阵
        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.POSITIVE_INFINITY } }
        dtw[0][0] = 0.0
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = distMatrix[i - 1][j - 1]
                dtw[i][j] = cost + minOf(
                    dtw[i - 1][j],      // 插入
                    dtw[i][j - 1],      // 删除
                    dtw[i - 1][j - 1]   // 匹配
                )
            }
        }
        
        return dtw[n][m]
    }
    
    /**
     * 欧氏距离
     */
    private fun euclideanDistance(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val diff = a[i] - b[i]
            sum += diff * diff
        }
        return kotlin.math.sqrt(sum)
    }
}

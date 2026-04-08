package com.nekoplayer.audio.fingerprint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 音频指纹单元测试
 */
class AudioFingerprintTest {
    
    @Test
    fun testChromaprintExtraction() {
        val fingerprinter = ChromaprintFingerprinter()
        
        // 生成测试音频（1秒16kHz正弦波）
        val sampleRate = 16000
        val duration = 1  // 1秒
        val audioData = ShortArray(sampleRate * duration) { i ->
            (kotlin.math.sin(2 * kotlin.math.PI * 440 * i / sampleRate) * 10000).toInt().toShort()
        }
        
        // 提取指纹
        val fingerprint = fingerprinter.extractFingerprint(audioData)
        
        // 验证维度
        assertEquals(128, fingerprint.size, "Fingerprint should be 128-dimensional")
        
        // 验证归一化（L2范数≈1）
        val norm = kotlin.math.sqrt(fingerprint.map { it * it }.sum().toDouble())
        assertTrue(kotlin.math.abs(norm - 1.0) < 0.01, "Fingerprint should be L2 normalized")
    }
    
    @Test
    fun testVectorIndex() {
        val index = VectorIndex(dim = 128, maxElements = 1000)
        
        // 添加测试向量
        val testData = listOf(
            "song1" to FloatArray(128) { kotlin.random.Random.nextFloat() }.normalize(),
            "song2" to FloatArray(128) { kotlin.random.Random.nextFloat() }.normalize(),
            "song3" to FloatArray(128) { kotlin.random.Random.nextFloat() }.normalize()
        )
        
        testData.forEach { (id, vector) ->
            index.add(id, vector)
        }
        
        assertEquals(3, index.size(), "Index should contain 3 items")
        
        // 搜索测试
        val query = testData[0].second
        val results = index.search(query, k = 3)
        
        assertTrue(results.isNotEmpty(), "Search should return results")
        assertEquals("song1", results[0].id, "First result should be the query itself")
        assertTrue(results[0].distance < 0.01f, "Self distance should be near zero")
    }
    
    @Test
    fun testVectorIndexMemoryEfficiency() {
        val index = VectorIndex(dim = 128, maxElements = 10000)
        
        // 添加1000个向量
        repeat(1000) { i ->
            val vector = FloatArray(128) { kotlin.random.Random.nextFloat() }.normalize()
            index.add("song$i", vector)
        }
        
        val memoryMB = index.estimatedMemoryMB()
        println("Index memory usage: $memoryMB MB for 1000 songs")
        
        // 验证内存占用合理（应该小于10MB）
        assertTrue(memoryMB < 10f, "Memory usage should be less than 10MB for 1000 songs")
    }
    
    @Test
    fun testDTW() {
        val dtw = DynamicTimeWarping()
        
        // 创建两个相似的序列
        val seqA = Array(10) { i -> FloatArray(32) { i.toFloat() / 10 } }
        val seqB = Array(10) { i -> FloatArray(32) { i.toFloat() / 10 } }
        
        val distance = dtw.compute(seqA, seqB)
        
        // 相同序列的距离应该接近0
        assertTrue(distance < 0.1, "Distance between identical sequences should be near zero")
    }
    
    @Test
    fun testMelFilterCreation() {
        val fingerprinter = ChromaprintFingerprinter()
        
        // 提取时序指纹测试梅尔滤波器
        val audioData = ShortArray(16000 * 2) { i ->  
            (kotlin.math.sin(2 * kotlin.math.PI * 880 * i / 16000) * 5000).toInt().toShort()
        }
        
        val temporalFingerprint = fingerprinter.extractTemporalFingerprint(audioData)
        
        assertTrue(temporalFingerprint.isNotEmpty(), "Should produce temporal fingerprints")
        assertEquals(32, temporalFingerprint[0].size, "Each frame should have 32 mel bins")
    }
    
    private fun FloatArray.normalize(): FloatArray {
        val norm = kotlin.math.sqrt(this.map { it * it }.sum())
        return if (norm > 0) {
            FloatArray(this.size) { this[it] / norm }
        } else this
    }
}

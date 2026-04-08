package com.nekoplayer.audio.fingerprint

/**
 * 音频指纹提取器
 * 
 * 将音频信号转换为紧凑的指纹向量，用于快速匹配
 */
interface AudioFingerprinter {
    
    /**
     * 提取音频指纹
     * @param audioData PCM音频数据（16bit, 16kHz, 单声道）
     * @return 指纹向量（128维或256维）
     */
    fun extractFingerprint(audioData: ShortArray): FloatArray
    
    /**
     * 从文件提取指纹
     * @param filePath 音频文件路径
     * @return 指纹向量
     */
    suspend fun extractFromFile(filePath: String): FloatArray?
    
    /**
     * 提取音频片段指纹（用于哼唱识别）
     * @param audioData PCM数据
     * @param sampleRate 采样率
     * @return 时序指纹（多帧）
     */
    fun extractTemporalFingerprint(
        audioData: ShortArray, 
        sampleRate: Int = 16000
    ): List<FloatArray>
    
    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 512
        const val HOP_SIZE = 256
        const val FINGERPRINT_DIM = 128
    }
}

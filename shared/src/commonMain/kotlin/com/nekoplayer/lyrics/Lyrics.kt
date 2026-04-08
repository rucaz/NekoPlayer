package com.nekoplayer.lyrics

/**
 * 歌词行数据类
 * 
 * @param timeMs 时间戳（毫秒）
 * @param content 歌词内容
 * @param translation 翻译内容（可选）
 */
data class LyricLine(
    val timeMs: Long,
    val content: String,
    val translation: String? = null
) {
    /**
     * 获取格式化的时间字符串 [mm:ss.xx]
     */
    fun formattedTime(): String {
        val minutes = timeMs / 60000
        val seconds = (timeMs % 60000) / 1000
        val centiseconds = (timeMs % 1000) / 10
        return String.format("[%02d:%02d.%02d]", minutes, seconds, centiseconds)
    }
    
    companion object {
        /**
         * 将时间字符串解析为毫秒
         * 支持格式: [mm:ss], [mm:ss.xx], [mm:ss.xxx]
         */
        fun parseTime(timeStr: String): Long {
            val regex = "\\[(\\d{2}):(\\d{2})[.:]?(\\d{0,3})?\\]".toRegex()
            val matchResult = regex.find(timeStr) ?: return 0L
            
            val (minutes, seconds, centis) = matchResult.destructured
            val min = minutes.toLongOrNull() ?: 0L
            val sec = seconds.toLongOrNull() ?: 0L
            
            // 处理不同精度的时间格式
            val centi = when (centis.length) {
                0 -> 0L
                1 -> centis.toLongOrNull()?.times(100) ?: 0L  // [mm:ss.x] -> x00ms
                2 -> centis.toLongOrNull()?.times(10) ?: 0L   // [mm:ss.xx] -> xx0ms
                3 -> centis.toLongOrNull() ?: 0L              // [mm:ss.xxx] -> xxxms
                else -> centis.take(3).toLongOrNull() ?: 0L
            }
            
            return min * 60000 + sec * 1000 + centi
        }
    }
}

/**
 * 歌词数据类
 * 
 * @param songId 歌曲ID
 * @param source 来源 (bilibili/migu)
 * @param lines 歌词行列表（已按时间排序）
 * @param hasTranslation 是否包含翻译
 */
data class Lyrics(
    val songId: String,
    val source: String,
    val lines: List<LyricLine>,
    val hasTranslation: Boolean = false
) {
    /**
     * 获取指定时间对应的歌词行索引
     * 
     * @param timeMs 当前播放时间（毫秒）
     * @return 当前应高亮的歌词行索引，如果没有匹配返回 -1
     */
    fun getCurrentLineIndex(timeMs: Long): Int {
        if (lines.isEmpty()) return -1
        
        // 二分查找优化
        var left = 0
        var right = lines.size - 1
        var result = -1
        
        while (left <= right) {
            val mid = (left + right) / 2
            if (lines[mid].timeMs <= timeMs) {
                result = mid
                left = mid + 1
            } else {
                right = mid - 1
            }
        }
        
        return result
    }
    
    /**
     * 获取当前时间的歌词行
     */
    fun getCurrentLine(timeMs: Long): LyricLine? {
        val index = getCurrentLineIndex(timeMs)
        return if (index >= 0) lines[index] else null
    }
    
    /**
     * 获取指定索引歌词行的进度百分比
     * 
     * @param index 歌词行索引
     * @param currentTimeMs 当前时间
     * @return 0.0 - 1.0 之间的进度值
     */
    fun getLineProgress(index: Int, currentTimeMs: Long): Float {
        if (index < 0 || index >= lines.size) return 0f
        
        val currentLine = lines[index]
        val nextLine = lines.getOrNull(index + 1)
        
        val lineStart = currentLine.timeMs
        val lineEnd = nextLine?.timeMs ?: (lineStart + 5000) // 默认5秒
        
        if (currentTimeMs <= lineStart) return 0f
        if (currentTimeMs >= lineEnd) return 1f
        
        return (currentTimeMs - lineStart).toFloat() / (lineEnd - lineStart)
    }
    
    /**
     * 导出为标准LRC格式字符串
     */
    fun toLrcString(): String {
        val sb = StringBuilder()
        lines.forEach { line ->
            sb.append(line.formattedTime())
            sb.append(line.content)
            sb.appendLine()
            
            line.translation?.let { trans ->
                sb.append(line.formattedTime())
                sb.append("> ")
                sb.append(trans)
                sb.appendLine()
            }
        }
        return sb.toString()
    }
    
    companion object {
        /**
         * 空歌词对象
         */
        fun empty(songId: String = "", source: String = ""): Lyrics {
            return Lyrics(songId, source, emptyList())
        }
    }
}

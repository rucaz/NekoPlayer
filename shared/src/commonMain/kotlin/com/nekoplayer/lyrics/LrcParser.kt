package com.nekoplayer.lyrics

/**
 * LRC 歌词文件解析器
 * 
 * 支持标准LRC格式：
 * - 基础时间标签: [mm:ss.xx]
 * - 偏移标签: [offset:+/-毫秒]
 * - 元信息标签: [ti:标题], [ar:歌手], [al:专辑], [by:编辑]
 * - 翻译行: 以 > 开头的行
 * - 逐字歌词 (Enhanced LRC): [mm:ss.xx]word [mm:ss.xx]word
 */
class LrcParser {
    
    /**
     * 解析LRC格式字符串
     * 
     * @param lrcContent LRC文件内容
     * @param songId 歌曲ID
     * @param source 来源
     * @return 解析后的Lyrics对象
     */
    fun parse(lrcContent: String, songId: String = "", source: String = ""): Lyrics {
        val lines = lrcContent.lines()
        val lyricLines = mutableListOf<LyricLine>()
        val translations = mutableMapOf<Long, String>()
        
        var offset = 0L
        
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            
            // 解析偏移标签 [offset:+/-毫秒]
            if (line.startsWith("[offset:")) {
                offset = parseOffsetTag(line)
                return@forEach
            }
            
            // 解析元信息标签 (跳过)
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || 
                line.startsWith("[al:") || line.startsWith("[by:")) {
                return@forEach
            }
            
            // 解析时间标签行
            val timeTags = extractTimeTags(line)
            if (timeTags.isEmpty()) return@forEach
            
            // 提取歌词内容（移除所有时间标签）
            val content = removeTimeTags(line).trim()
            
            // 处理翻译行 (以 > 开头)
            if (content.startsWith("> ")) {
                val transContent = content.substring(2)
                timeTags.forEach { timeMs ->
                    translations[timeMs + offset] = transContent
                }
            } else {
                // 普通歌词行
                timeTags.forEach { timeMs ->
                    lyricLines.add(LyricLine(
                        timeMs = timeMs + offset,
                        content = content,
                        translation = null
                    ))
                }
            }
        }
        
        // 合并翻译
        val mergedLines = lyricLines.map { line ->
            val trans = translations[line.timeMs]
            if (trans != null) {
                line.copy(translation = trans)
            } else {
                line
            }
        }
        
        // 按时间排序
        val sortedLines = mergedLines.sortedBy { it.timeMs }
        
        return Lyrics(
            songId = songId,
            source = source,
            lines = sortedLines,
            hasTranslation = translations.isNotEmpty()
        )
    }
    
    /**
     * 解析偏移标签
     */
    private fun parseOffsetTag(line: String): Long {
        val regex = "\\[offset:([+-]?\\d+)\\]".toRegex()
        val match = regex.find(line) ?: return 0L
        return match.groupValues[1].toLongOrNull() ?: 0L
    }
    
    /**
     * 从行中提取所有时间标签
     */
    private fun extractTimeTags(line: String): List<Long> {
        val regex = "\\[(\\d{2}):(\\d{2})[.:]?(\\d{0,3})?\\]".toRegex()
        return regex.findAll(line).map { match ->
            LyricLine.parseTime(match.value)
        }.toList()
    }
    
    /**
     * 移除行中所有时间标签
     */
    private fun removeTimeTags(line: String): String {
        return line.replace("\\[\\d{2}:\\d{2}[.:]?\\d{0,3}?\\]".toRegex(), "")
    }
    
    /**
     * 检查是否是有效的LRC内容
     */
    fun isValidLrc(content: String): Boolean {
        // 至少包含一个时间标签
        val timeTagRegex = "\\[\\d{2}:\\d{2}".toRegex()
        return timeTagRegex.containsMatchIn(content)
    }
    
    /**
     * 解析增强型LRC (逐字歌词)
     * 格式: [mm:ss.xx]word [mm:ss.xx]word
     */
    fun parseEnhanced(lrcContent: String, songId: String = "", source: String = ""): Lyrics {
        // 增强型LRC转标准LRC
        // 将逐字时间合并为行时间
        val standardLrc = lrcContent.replace(
            "\\[(\\d{2}:\\d{2}\\.\\d{2,3})\\]([^\\[]+)".toRegex(),
            "$2"
        ).replace(
            "([^\\n]*)\\[(\\d{2}:\\d{2}\\.\\d{2,3})\\]".toRegex(),
            "[$2]$1"
        )
        
        return parse(standardLrc, songId, source)
    }
    
    companion object {
        /**
         * 单例实例
         */
        val Instance = LrcParser()
    }
}

package com.nekoplayer.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class LyricLineTest {
    
    @Test
    fun `test formattedTime converts milliseconds correctly`() {
        val line = LyricLine(timeMs = 65000, content = "Test")
        assertEquals("[01:05.00]", line.formattedTime())
        
        val line2 = LyricLine(timeMs = 123456, content = "Test")
        assertEquals("[02:03.45]", line2.formattedTime())
        
        val line3 = LyricLine(timeMs = 60000, content = "Test")
        assertEquals("[01:00.00]", line3.formattedTime())
    }
    
    @Test
    fun `test parseTime with standard format`() {
        assertEquals(65000L, LyricLine.parseTime("[01:05.00]"))
        assertEquals(123456L, LyricLine.parseTime("[02:03.45]"))
        assertEquals(60000L, LyricLine.parseTime("[01:00.00]"))
    }
    
    @Test
    fun `test parseTime with milliseconds`() {
        assertEquals(61100L, LyricLine.parseTime("[01:01.10]"))
        assertEquals(61500L, LyricLine.parseTime("[01:01.5]"))
        assertEquals(61123L, LyricLine.parseTime("[01:01.123]"))
    }
    
    @Test
    fun `test parseTime with dot separator`() {
        assertEquals(65000L, LyricLine.parseTime("[01:05.00]"))
        assertEquals(65100L, LyricLine.parseTime("[01:05.10]"))
    }
    
    @Test
    fun `test parseTime returns zero for invalid format`() {
        assertEquals(0L, LyricLine.parseTime("invalid"))
        assertEquals(0L, LyricLine.parseTime(""))
    }
}

class LyricsTest {
    
    private fun createSampleLyrics(): Lyrics {
        return Lyrics(
            songId = "test-song",
            source = "bilibili",
            lines = listOf(
                LyricLine(timeMs = 0, content = "第一行歌词"),
                LyricLine(timeMs = 5000, content = "第二行歌词"),
                LyricLine(timeMs = 10000, content = "第三行歌词"),
                LyricLine(timeMs = 15000, content = "第四行歌词")
            )
        )
    }
    
    @Test
    fun `test getCurrentLineIndex returns correct index`() {
        val lyrics = createSampleLyrics()
        
        assertEquals(0, lyrics.getCurrentLineIndex(0))
        assertEquals(0, lyrics.getCurrentLineIndex(1000))
        assertEquals(0, lyrics.getCurrentLineIndex(4999))
        assertEquals(1, lyrics.getCurrentLineIndex(5000))
        assertEquals(2, lyrics.getCurrentLineIndex(10000))
        assertEquals(3, lyrics.getCurrentLineIndex(20000))
    }
    
    @Test
    fun `test getCurrentLineIndex returns minus one for empty lyrics`() {
        val emptyLyrics = Lyrics.empty()
        assertEquals(-1, emptyLyrics.getCurrentLineIndex(1000))
    }
    
    @Test
    fun `test getCurrentLine returns correct line`() {
        val lyrics = createSampleLyrics()
        
        assertEquals("第一行歌词", lyrics.getCurrentLine(0)?.content)
        assertEquals("第二行歌词", lyrics.getCurrentLine(5000)?.content)
        assertEquals("第三行歌词", lyrics.getCurrentLine(10000)?.content)
        assertNull(lyrics.getCurrentLine(-100))
    }
    
    @Test
    fun `test getLineProgress returns correct value`() {
        val lyrics = createSampleLyrics()
        
        // 第一行开始
        assertEquals(0f, lyrics.getLineProgress(0, 0))
        
        // 第一行中间
        val progress = lyrics.getLineProgress(0, 2500)
        assertTrue(progress > 0f && progress < 1f)
        
        // 第一行结束（接近第二行）
        assertTrue(lyrics.getLineProgress(0, 4999) < 1f)
    }
    
    @Test
    fun `test toLrcString generates correct format`() {
        val lyrics = Lyrics(
            songId = "test",
            source = "test",
            lines = listOf(
                LyricLine(timeMs = 65000, content = "Test Line", translation = "测试行")
            ),
            hasTranslation = true
        )
        
        val lrcString = lyrics.toLrcString()
        assertTrue(lrcString.contains("[01:05.00]Test Line"))
        assertTrue(lrcString.contains("[01:05.00]> 测试行"))
    }
}

class LrcParserTest {
    
    private val parser = LrcParser()
    
    @Test
    fun `test parse basic LRC format`() {
        val lrcContent = """
            [00:00.00]第一行
            [00:05.50]第二行
            [00:10.00]第三行
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent, "song-1", "bilibili")
        
        assertEquals("song-1", lyrics.songId)
        assertEquals("bilibili", lyrics.source)
        assertEquals(3, lyrics.lines.size)
        assertEquals("第一行", lyrics.lines[0].content)
        assertEquals(0L, lyrics.lines[0].timeMs)
        assertEquals(5500L, lyrics.lines[1].timeMs)
        assertEquals(10000L, lyrics.lines[2].timeMs)
    }
    
    @Test
    fun `test parse with offset`() {
        val lrcContent = """
            [offset:+500]
            [00:00.00]第一行
            [00:05.00]第二行
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals(500L, lyrics.lines[0].timeMs)  // 0 + 500
        assertEquals(5500L, lyrics.lines[1].timeMs) // 5000 + 500
    }
    
    @Test
    fun `test parse with translation`() {
        val lrcContent = """
            [00:00.00]Hello
            [00:00.00]> 你好
            [00:05.00]World
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals(2, lyrics.lines.size)
        assertEquals("Hello", lyrics.lines[0].content)
        assertEquals("你好", lyrics.lines[0].translation)
        assertTrue(lyrics.hasTranslation)
        assertNull(lyrics.lines[1].translation)
    }
    
    @Test
    fun `test parse with metadata tags`() {
        val lrcContent = """
            [ti:Test Song]
            [ar:Test Artist]
            [al:Test Album]
            [by:Test Editor]
            [00:00.00]歌词内容
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals(1, lyrics.lines.size)
        assertEquals("歌词内容", lyrics.lines[0].content)
    }
    
    @Test
    fun `test parse multiple time tags for same content`() {
        val lrcContent = """
            [00:00.00][00:10.00][00:20.00]重复歌词
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals(3, lyrics.lines.size)
        assertEquals("重复歌词", lyrics.lines[0].content)
        assertEquals("重复歌词", lyrics.lines[1].content)
        assertEquals("重复歌词", lyrics.lines[2].content)
        assertEquals(0L, lyrics.lines[0].timeMs)
        assertEquals(10000L, lyrics.lines[1].timeMs)
        assertEquals(20000L, lyrics.lines[2].timeMs)
    }
    
    @Test
    fun `test isValidLrc returns true for valid content`() {
        assertTrue(parser.isValidLrc("[00:00.00]Test"))
        assertTrue(parser.isValidLrc("[ti:Title]\n[00:00.00]Test"))
    }
    
    @Test
    fun `test isValidLrc returns false for invalid content`() {
        assertFalse(parser.isValidLrc("Plain text"))
        assertFalse(parser.isValidLrc(""))
        assertFalse(parser.isValidLrc("[ti:Title]\n[ar:Artist]"))
    }
    
    @Test
    fun `test parse empty lines and whitespace`() {
        val lrcContent = """
            
            [00:00.00]第一行
            
            [00:05.00]第二行
            
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals(2, lyrics.lines.size)
    }
    
    @Test
    fun `test lines are sorted by time`() {
        val lrcContent = """
            [00:10.00]第二行
            [00:00.00]第一行
            [00:05.00]第三行
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent)
        
        assertEquals("第一行", lyrics.lines[0].content)
        assertEquals("第三行", lyrics.lines[1].content)
        assertEquals("第二行", lyrics.lines[2].content)
    }
    
    @Test
    fun `test parse real world LRC format`() {
        val lrcContent = """
            [ti:Test Song]
            [ar:Test Artist]
            [offset:0]
            
            [00:00.00]前奏音乐
            [00:05.23]这是第一句歌词
            [00:09.85]这是第二句歌词，比较长
            [00:14.50]> This is translation
            [00:15.20]第三句
            
            [00:20.00]结束
        """.trimIndent()
        
        val lyrics = parser.parse(lrcContent, "real-song", "migu")
        
        assertEquals("real-song", lyrics.songId)
        assertEquals("migu", lyrics.source)
        assertTrue(lyrics.lines.size >= 4)
        assertTrue(lyrics.hasTranslation)
    }
}

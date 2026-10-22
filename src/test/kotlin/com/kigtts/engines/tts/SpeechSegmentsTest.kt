package com.kigtts.engines.tts

import org.junit.Assert.*
import org.junit.Test

class SpeechSegmentsTest {
    @Test fun sentencesKeepExactTextAndFirstAudioOnlyRequiresSmallFirstSentence() {
        val text = "你好，先播放这一句。" + "接下来还要处理很长的内容，".repeat(40)
        val pieces = speechSegments(text).toList()
        assertEquals("你好，先播放这一句。", pieces.first())
        assertEquals(text, pieces.joinToString(""))
        assertTrue(pieces.drop(1).all { it.length <= 96 })
    }
    @Test fun unpunctuatedTextEmojiAndTinyProviderLimitsCannotLoseOrSplitSurrogates() {
        for (text in listOf("中".repeat(400), "abc😀def".repeat(50), "supercalifragilistic".repeat(40))) {
            val pieces = speechSegments(text, 3, 3).toList()
            assertEquals(text, pieces.joinToString(""))
            assertTrue(pieces.all { it.isNotEmpty() && it.length <= 3 && !it.last().isHighSurrogate() && !it.first().isLowSurrogate() })
        }
    }
    @Test fun decimalAndWhitespaceArePreserved() {
        val text = "Price 3.14 then read this sentence. Next line\n\n done"
        assertEquals(text, speechSegments(text).joinToString(""))
        assertTrue(speechSegments(text).first().contains("3.14"))
    }
}

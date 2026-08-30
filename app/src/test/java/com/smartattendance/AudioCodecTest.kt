package com.smartattendance

import com.smartattendance.audio.AudioChallengeCodec
import com.smartattendance.audio.AudioChallengeDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * تست‌های Audio Challenge Codec:
 * • Encode → Decode رفت‌وبرگشت
 * • مقاومت در برابر نویز سفید کلاس
 * • تشخیص در جریان استریمی فریم‌به‌فریم (شبیه میکروفون واقعی)
 * • رد کردن سیگنال بی‌چالش / خراب
 */
class AudioCodecTest {

    private fun feedInChunks(pcm: ShortArray, chunk: Int = 2048): String? {
        val decoder = AudioChallengeDecoder()
        var result: String? = null
        var i = 0
        while (i < pcm.size) {
            val end = minOf(i + chunk, pcm.size)
            result = decoder.feed(pcm.copyOfRange(i, end))
            if (result != null) return result
            i += chunk
        }
        return result
    }

    @Test
    fun `encode decode roundtrip works`() {
        val token = "A81F03BC"
        val pcm = AudioChallengeCodec.encode(token)
        val decoded = feedInChunks(pcm)
        assertEquals(token, decoded)
    }

    @Test
    fun `all hex tokens decode correctly`() {
        val tokens = listOf("00000000", "FFFFFFFF", "12345678", "9ABCDEF0", "0F1E2D3C")
        tokens.forEach { token ->
            val decoded = feedInChunks(AudioChallengeCodec.encode(token))
            assertEquals("token=$token", token, decoded)
        }
    }

    @Test
    fun `decodes under classroom level white noise`() {
        val token = "B7E4C291"
        val clean = AudioChallengeCodec.encode(token)
        val noisy = ShortArray(clean.size) { i ->
            (clean[i] + Random.nextInt(-1_800, 1_800)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        val decoded = feedInChunks(noisy)
        assertEquals(token, decoded)
    }

    @Test
    fun `decodes in streaming mode with leading silence`() {
        val token = "5C0FFEE1"
        val silence = ShortArray(AudioChallengeCodec.SAMPLE_RATE) // ۱ ثانیه سکوت اول
        val pcm = silence + AudioChallengeCodec.encode(token) + silence
        val decoded = feedInChunks(pcm)
        assertEquals(token, decoded)
    }

    @Test
    fun `silence yields no token`() {
        val silence = ShortArray(AudioChallengeCodec.SAMPLE_RATE * 2)
        assertNull(feedInChunks(silence))
    }

    @Test
    fun `truncated signal without endmark is rejected`() {
        val pcm = AudioChallengeCodec.encode("1234ABCD")
        val truncated = pcm.copyOfRange(0, pcm.size - AudioChallengeCodec.msToSamples(140L))
        val decoded = feedInChunks(truncated)
        assertNull("بدون Endmark نباید توکن پذیرفته شود", decoded)
    }

    @Test
    fun `encoder rejects malformed tokens`() {
        val invalid = listOf("", "ABC", "GHIJKLMN", "123456789")
        invalid.forEach { token ->
            val threw = runCatching { AudioChallengeCodec.encode(token) }.isFailure
            assertTrue("token=\"$token\" باید رد شود", threw)
        }
    }

    @Test
    fun `frame timeline is consistent`() {
        val pcm = AudioChallengeCodec.encode("00000000")
        val expected = AudioChallengeCodec.msToSamples(150L) +
            8 * AudioChallengeCodec.msToSamples(90L) +
            AudioChallengeCodec.msToSamples(120L)
        assertEquals(expected, pcm.size)
        assertTrue("مدت کل چالش باید زیر ۱ ثانیه باشد", pcm.size < AudioChallengeCodec.SAMPLE_RATE)
    }
}

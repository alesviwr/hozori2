package com.smartattendance.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ═════════════════════════════════════════════════════════════════
 *  Audio Challenge Codec — FSK شانزده‌تنه + دیکودر Goertzel
 * ═════════════════════════════════════════════════════════════════
 *
 *  ساختار سیگنال (کاملاً Pure-Kotlin و قابل Unit Test):
 *
 *    ┌──────────┬───────────────────────────────┬─────────┐
 *    │ Preamble │ 8 فریم × ۹۰ms (هر فریم ۴ بیت) │ Endmark │
 *    │  ۷۵۰Hz   │  ۱۰۰۰Hz تا ۳۸۵۰Hz (گام ۱۹۰)   │ ۴۳۰۰Hz  │
 *    │  ۱۵۰ms   │            ۷۲۰ms              │  ۱۲۰ms  │
 *    └──────────┴───────────────────────────────┴─────────┘
 *
 *  • توکن چالش: ۸ کاراکتر هگز = ۳۲ بیت = ۸ نیبل = ۸ تن
 *  • هر تن ۵ms Fade در ابتدا/انتها دارد تا کلیک صوتی ایجاد نشود
 *  • دیکودر: جستجوی Preamble با پنجره لغزان + Goertzel روی ۱۶ فرکانس
 *  • Session-bound بودن توکن را سرور تضمین می‌کند؛ این کد فقط «حمل» است
 */
object AudioChallengeCodec {

    const val SAMPLE_RATE = 44_100
    const val TOKEN_LENGTH = 8
    const val AMPLITUDE = 12_000.0

    const val PREAMBLE_FREQ = 750.0
    const val ENDMARK_FREQ = 4300.0

    const val PREAMBLE_MS = 150L
    const val FRAME_MS = 90L
    const val CORE_MS = 80L      // پنجره تحلیل داخل هر فریم (بدون Fade)
    const val FADE_MS = 5L
    const val ENDMARK_MS = 120L

    val dataFrequencies: DoubleArray = DoubleArray(16) { 1000.0 + it * 190.0 }

    val msToSamples = { ms: Long -> (ms * SAMPLE_RATE / 1000L).toInt() }

    // ─────────────────────── Encoder ───────────────────────

    /** تولید PCM 16bit برای یک توکن ۸ رقمی هگز */
    fun encode(token: String): ShortArray {
        require(token.length == TOKEN_LENGTH && token.all { it in "0123456789abcdefABCDEF" }) {
            "token must be $TOKEN_LENGTH hex chars"
        }
        val preambleLen = msToSamples(PREAMBLE_MS)
        val frameLen = msToSamples(FRAME_MS)
        val endLen = msToSamples(ENDMARK_MS)
        val total = preambleLen + frameLen * TOKEN_LENGTH + endLen
        val out = ShortArray(total)

        var pos = writeTone(out, pos = 0, length = preambleLen, freq = PREAMBLE_FREQ)
        for (i in 0 until TOKEN_LENGTH) {
            val nibble = Character.digit(token[i], 16)
            pos = writeTone(out, pos, frameLen, dataFrequencies[nibble])
        }
        writeTone(out, pos, endLen, ENDMARK_FREQ)
        return out
    }

    private fun writeTone(out: ShortArray, pos: Int, length: Int, freq: Double): Int {
        val fade = msToSamples(FADE_MS)
        val omega = 2.0 * PI * freq / SAMPLE_RATE
        for (i in 0 until length) {
            var gain = 1.0
            if (i < fade) gain = 0.5 - 0.5 * cos(PI * i / fade)
            if (i >= length - fade) gain = 0.5 - 0.5 * cos(PI * (length - i) / fade)
            out[pos + i] = (AMPLITUDE * gain * sin(omega * i)).toInt().toShort()
        }
        return pos + length
    }

    // ─────────────────────── Goertzel ───────────────────────

    /** توان Goertzel روی بازه [offset, offset+length) در فرکانس دلخواه */
    fun goertzelPower(samples: ShortArray, offset: Int, length: Int, freq: Double): Double {
        val omega = 2.0 * PI * freq / SAMPLE_RATE
        val coeff = 2.0 * cos(omega)
        var s1 = 0.0
        var s2 = 0.0
        val end = offset + length
        for (i in offset until end) {
            val s0 = samples[i] + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2
    }
}

/**
 * دیکودر استریمی — با فریم‌های میکروفون تغذیه می‌شود و توکن را برمی‌گرداند.
 * پیاده‌سازی Stateful است؛ پس از تشخیص موفق، بافر کامل پاک می‌شود.
 *
 * راهبرد آستانه‌گذاری (Self-referenced):
 *   توان هر فریم نسبت به توان Preamble سنجیده می‌شود، نه میانگین پنجره‌های همپوشان —
 *   چون پنجره‌های نزدیک Preamble خودشان انرژی بالایی دارند و میانگین را خراب می‌کنند.
 *   نسبت انتظاری: توان تون خالص ~ (طول پنجره)² → (CORE/PREAMBLE)² ≈ ۰٫۲۸ و
 *   (ENDMARK/PREAMBLE)² ≈ ۰٫۶۴؛ با حاشیه اطمینان SNR÷۵ و ÷۳ کف‌ها تنظیم شده‌اند.
 */
class AudioChallengeDecoder {

    private val buffer = ArrayDeque<Short>()
    private val codec = AudioChallengeCodec

    private val needSamples: Int =
        codec.msToSamples(codec.PREAMBLE_MS + codec.FRAME_MS * codec.TOKEN_LENGTH + codec.ENDMARK_MS)

    /** حداکثر حافظه بافر = ۳ برابر سیگنال کامل؛ قدیمی‌ترین داده فقط در سرریز حذف می‌شود */
    private val maxBuffer: Int = needSamples * 3

    /** @return توکن ۸ رقمی هگز در صورت تشخیص، در غیر این صورت null */
    fun feed(samples: ShortArray): String? {
        for (s in samples) buffer.addLast(s)

        if (buffer.size < needSamples) return null

        val array = buffer.toShortArray()
        val token = detect(array)
        if (token != null) {
            buffer.clear()
            return token
        }
        // بدون تشخیص: داده‌ها حفظ می‌شوند (Preamble نباید گم شود)؛
        // فقط در سرریز، قدیمی‌ترین نمونه‌ها حذف می‌شوند.
        if (buffer.size > maxBuffer) {
            repeat(buffer.size - maxBuffer) { buffer.removeFirst() }
        }
        return null
    }

    fun reset() = buffer.clear()

    private fun detect(samples: ShortArray): String? {
        val preambleLen = codec.msToSamples(codec.PREAMBLE_MS)
        val frameLen = codec.msToSamples(codec.FRAME_MS)
        val coreLen = codec.msToSamples(codec.CORE_MS)
        val fadeLen = codec.msToSamples(codec.FADE_MS)
        val endLen = codec.msToSamples(codec.ENDMARK_MS)
        val totalNeeded = preambleLen + frameLen * codec.TOKEN_LENGTH + endLen
        if (samples.size < totalNeeded) return null
        val lastStart = samples.size - totalNeeded

        // ۱) جستجوی درشت Preamble با گام ۱۰ms
        var bestPos = -1
        var bestPower = 0.0
        var pos = 0
        while (pos <= lastStart) {
            val p = codec.goertzelPower(samples, pos, preambleLen, codec.PREAMBLE_FREQ)
            if (p > bestPower) {
                bestPower = p
                bestPos = pos
            }
            pos += codec.msToSamples(10L)
        }
        if (bestPos < 0) return null

        // ۲) تنظیم دقیق موقعیت با گام ۱ms در همسایگی ±۱۰ms
        val coarseTol = codec.msToSamples(10L)
        val fineStride = codec.msToSamples(1L)
        var refPos = bestPos
        var refPower = bestPower
        var fine = maxOf(0, bestPos - coarseTol)
        val fineEnd = minOf(lastStart, bestPos + coarseTol)
        while (fine <= fineEnd) {
            val p = codec.goertzelPower(samples, fine, preambleLen, codec.PREAMBLE_FREQ)
            if (p > refPower) {
                refPower = p
                refPos = fine
            }
            fine += fineStride
        }

        // ۳) Preamble باید انرژی مطلق کافی داشته باشد (رد سیگنال ساکت/تهی)
        if (refPower < ABS_MIN) return null

        // ۴) کف توان فریم‌ها و Endmark نسبت به Preamble
        val frameFloor = refPower * FRAME_FLOOR_RATIO
        val endFloor = refPower * END_FLOOR_RATIO

        val dataStart = refPos + preambleLen
        val nibbles = IntArray(codec.TOKEN_LENGTH)
        for (i in 0 until codec.TOKEN_LENGTH) {
            val frameOffset = dataStart + i * frameLen + fadeLen
            if (frameOffset + coreLen > samples.size) return null
            var topFreq = -1
            var topPower = 0.0
            var secondPower = 0.0
            for (f in codec.dataFrequencies.indices) {
                val p = codec.goertzelPower(samples, frameOffset, coreLen, codec.dataFrequencies[f])
                when {
                    p > topPower -> {
                        secondPower = topPower
                        topPower = p
                        topFreq = f
                    }
                    p > secondPower -> secondPower = p
                }
            }
            if (topFreq < 0) return null
            if (topPower < frameFloor) return null
            if (secondPower > 0 && topPower / secondPower < RATIO) return null
            nibbles[i] = topFreq
        }

        // ۵) بررسی Endmark
        val endOffset = dataStart + codec.TOKEN_LENGTH * frameLen
        if (endOffset + endLen > samples.size) return null
        val endPower = codec.goertzelPower(samples, endOffset, endLen, codec.ENDMARK_FREQ)
        if (endPower < endFloor) return null

        return nibbles.joinToString("") { "0123456789ABCDEF"[it].toString() }
    }

    private companion object {
        /** (۳۵۲۸/۶۶۱۵)² ≈ ۰٫۲۸۴ → با حاشیه SNR≈۵ → کف ۰٫۰۵ */
        const val FRAME_FLOOR_RATIO = 0.05
        /** (۵۲۹۲/۶۶۱۵)² ≈ ۰٫۶۴۰ → با حاشیه SNR≈۳ → کف ۰٫۲۰ */
        const val END_FLOOR_RATIO = 0.20
        const val RATIO = 1.15
        const val ABS_MIN = 1e6
    }
}

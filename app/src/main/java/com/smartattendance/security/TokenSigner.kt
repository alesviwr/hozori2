package com.smartattendance.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * امضای HMAC-SHA256 برای توکن‌های QR و نشست‌ها.
 *
 * ⚠️ نکته امنیتی: در تولید، کلید secret باید «فقط» روی سرور نگهداری شود.
 * در این پروژه، همین کلاس داخل MockBackend نقش سرور را بازی می‌کند؛
 * کلاینت هرگز به secret دسترسی ندارد و فقط رشته توکن کامل را رد و بدل می‌کند.
 */
class TokenSigner(private val secret: ByteArray) {

    fun sign(data: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret, ALGORITHM))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHexString()
    }

    /** مقایسه زمان-ثابت برای جلوگیری از Timing Attack */
    fun verify(data: String, signatureHex: String): Boolean {
        val expected = sign(data)
        if (expected.length != signatureHex.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (expected[i].code xor signatureHex[i].code)
        return diff == 0
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        /** کلید تست — فقط برای Unit Test */
        fun testSecret(): ByteArray = ByteArray(32) { it.toByte() }
    }
}

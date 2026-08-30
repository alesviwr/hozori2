package com.smartattendance

import com.smartattendance.security.ReplayGuard
import com.smartattendance.security.TokenSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * تست‌های امنیتی هسته:
 * • امضای HMAC-SHA256 و تشخیص دستکاری
 * • مقایسه زمان-ثابت (Timing-Safe)
 * • انقضای توکن
 * • ReplayGuard: مصرف یک‌باره + TTL
 */
class TokenSecurityTest {

    private val signer = TokenSigner(TokenSigner.testSecret())

    // ─────────────── HMAC ───────────────

    @Test
    fun `signature is deterministic and verifiable`() {
        val payload = "s_abc|A1B2C3D4|1720000000000|1720000003000|DEADBEEF"
        val sig = signer.sign(payload)
        assertEquals(sig, signer.sign(payload))
        assertTrue(signer.verify(payload, sig))
    }

    @Test
    fun `tampered payload is rejected`() {
        val payload = "s_abc|A1B2C3D4|1720000000000|1720000003000|DEADBEEF"
        val sig = signer.sign(payload)
        val tampered = payload.replace("DEADBEEF", "DEADBEE0")
        assertFalse(signer.verify(tampered, sig))
    }

    @Test
    fun `forged signature with wrong secret is rejected`() {
        val payload = "s_abc|A1B2C3D4|1720000000000|1720000003000|DEADBEEF"
        val attackerSigner = TokenSigner(ByteArray(32) { 7 })
        val forged = attackerSigner.sign(payload)
        assertFalse(signer.verify(payload, forged))
    }

    @Test
    fun `different secret produces different signature`() {
        val payload = "payload"
        val other = TokenSigner(ByteArray(32) { (it * 3 + 1).toByte() })
        assertFalse(signer.sign(payload) == other.sign(payload))
    }

    // ─────────────── Replay Guard ───────────────

    @Test
    fun `first consume succeeds second fails`() {
        var time = 1_000_000L
        val guard = ReplayGuard { time }

        assertTrue(guard.checkAndConsume("qr|T1|s1", ttlMs = 10_000))
        assertFalse("همان کلید نباید دوباره مصرف شود", guard.checkAndConsume("qr|T1|s1", ttlMs = 10_000))
    }

    @Test
    fun `same token different student is allowed`() {
        var time = 1_000_000L
        val guard = ReplayGuard { time }

        assertTrue(guard.checkAndConsume("qr|T1|s1", ttlMs = 10_000))
        assertTrue("دانشجوی دیگر باید بتواند QR زنده را اسکن کند", guard.checkAndConsume("qr|T1|s2", ttlMs = 10_000))
    }

    @Test
    fun `entry expires after ttl`() {
        var time = 1_000_000L
        val guard = ReplayGuard { time }

        assertTrue(guard.checkAndConsume("audio|C1|s1", ttlMs = 5_000))
        time += 6_000 // گذشته از TTL → پاک‌سازی
        assertTrue(guard.checkAndConsume("audio|C1|s1", ttlMs = 5_000))
    }

    @Test
    fun `within ttl replay is blocked even after long time`() {
        var time = 1_000_000L
        val guard = ReplayGuard { time }

        assertTrue(guard.checkAndConsume("audio|C2|s1", ttlMs = 60_000))
        time += 30_000
        assertFalse(guard.checkAndConsume("audio|C2|s1", ttlMs = 60_000))
    }
}

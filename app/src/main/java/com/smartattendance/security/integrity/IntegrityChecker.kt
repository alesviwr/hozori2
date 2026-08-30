package com.smartattendance.security.integrity

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.smartattendance.data.remote.api.AttendanceApi
import com.smartattendance.data.remote.dto.IntegrityRequestDto
import com.smartattendance.domain.model.IntegrityVerdict
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** قرارداد بررسی یکپارچگی اپ — در کنار سایر عوامل، نه جایگزین آن‌ها */
interface IntegrityChecker {
    suspend fun verdict(nonce: String): IntegrityVerdict
}

/** پیاده‌سازی Mock — در حالت Backend شبیه‌سازی‌شده همیشه PASSES برمی‌گرداند */
class MockIntegrityChecker : IntegrityChecker {
    override suspend fun verdict(nonce: String): IntegrityVerdict {
        kotlinx.coroutines.delay(50)
        return IntegrityVerdict.PASSES
    }
}

/**
 * پیاده‌سازی واقعی Play Integrity API.
 *
 * نیازمندی‌های تولید (مستند در README):
 * ۱. اتصال اپ Firebase/Google Cloud و فعال‌سازی Play Integrity API
 * ۲. انتشار اپ روی Google Play (یا Internal Testing)
 * ۳. اعتبارسنجی توکن روی سرور (decrypt + verify integrity verdict)
 *
 * اگر Integrity در دسترس نباشد UNKNOWN برمی‌گردد — حضور را مسدود نمی‌کند
 * چون طبق طراحی، Integrity جایگزین سایر عوامل نیست.
 */
class PlayIntegrityChecker(
    private val context: Context,
    private val api: AttendanceApi,
) : IntegrityChecker {

    override suspend fun verdict(nonce: String): IntegrityVerdict = try {
        val manager = IntegrityManagerFactory.create(context)
        val token = suspendCancellableCoroutine { cont ->
            manager
                .requestIntegrityToken(
                    IntegrityTokenRequest.builder().setNonce(nonce).build(),
                )
                .addOnSuccessListener { cont.resume(it.token()) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        val response = api.integrity(IntegrityRequestDto(nonce = nonce, integrityToken = token))
        runCatching { IntegrityVerdict.valueOf(response.verdict) }.getOrDefault(IntegrityVerdict.UNKNOWN)
    } catch (_: Exception) {
        IntegrityVerdict.UNKNOWN
    }
}

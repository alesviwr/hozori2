package com.smartattendance.security

/**
 * محافظ Replay برای چالش‌ها.
 * هر کلید (مثلاً tokenId + studentId) فقط یک بار در بازه TTL قابل مصرف است.
 *
 * در تولید این منطق باید روی سرور (مثلاً با Redis SETNX + TTL) پیاده شود؛
 * اینجا همان دقت معنایی داخل MockBackend شبیه‌سازی شده است.
 */
class ReplayGuard(private val clock: () -> Long = System::currentTimeMillis) {

    private val consumed = HashMap<String, Long>()

    /**
     * @return true اگر اولین مصرف باشد و کلید مصرف‌شده علامت بخورد؛
     *         false اگر قبلاً مصرف شده (Replay) باشد.
     */
    @Synchronized
    fun checkAndConsume(key: String, ttlMs: Long): Boolean {
        val now = clock()
        consumed.entries.removeAll { now - it.value > ttlMs }
        return consumed.putIfAbsent(key, now) == null
    }

    /** فقط برای تست */
    @Synchronized
    fun clear() = consumed.clear()
}

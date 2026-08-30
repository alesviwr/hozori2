package com.smartattendance.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject

/**
 * پوشش امن BiometricPrompt.
 *
 * اصول:
 * • اثر انگشت خام هرگز ذخیره یا منتقل نمی‌شود — فقط «موفقیت» به‌عنوان اظهار (attestation) گزارش می‌شود.
 * • بیومتریک تنها «یکی از» عوامل است و به‌تنهایی حضور را معتبر نمی‌کند.
 * • سرور در کنار Device Binding و Integrity به این اظهار اعتماد مشروط می‌کند.
 */
class BiometricAuthenticator @Inject constructor() {

    /**
     * توجه: وقتی قرار است از CryptoObject استفاده شود، سطح لازم BIOMETRIC_STRONG است —
     * اندروید احراز هویت مبتنی بر Crypto را با BIOMETRIC_WEAK اصلاً نمی‌پذیرد (IllegalArgumentException).
     * پس این متد باید دقیقاً با همان سطحی چک شود که در authenticate() استفاده می‌شود، وگرنه
     * canAuthenticate() می‌گوید «قابل استفاده است» ولی authenticate() بلافاصله کرش می‌کند.
     */
    fun canAuthenticate(context: Context, requireStrong: Boolean = false): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(
                if (requireStrong) BiometricManager.Authenticators.BIOMETRIC_STRONG
                else BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * @param crypto   برای کلیدهای نیازمند تأیید بیومتریک (CryptoObject با Signature) — نیازمند BIOMETRIC_STRONG
     * @param onSuccess پس از موفقیت سنسور بیومتریک
     * @param onError   خطای غیرقابل بازیابی یا انصراف کاربر — این callback تضمین می‌کند که این متد هرگز باعث کرش برنامه نشود
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String,
        crypto: BiometricPrompt.CryptoObject? = null,
        onSuccess: () -> Unit,
        onError: (message: String) -> Unit,
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onError(errString.toString())
                    }
                },
            )

            // کلید حیاتی: اگر crypto پاس داده شده، حتماً BIOMETRIC_STRONG لازم است.
            val allowed = if (crypto != null) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeText)
                .setAllowedAuthenticators(allowed)
                .build()

            if (crypto != null) prompt.authenticate(info, crypto) else prompt.authenticate(info)
        } catch (t: Throwable) {
            // هر خطای غیرمنتظره (مثلاً عدم پشتیبانی سخت‌افزار از BIOMETRIC_STRONG، تنظیمات نامعتبر و...)
            // دیگر کرش نمی‌کند؛ به‌صورت خطای معمولی به UI گزارش می‌شود.
            onError(t.message ?: "خطای احراز هویت بیومتریک")
        }
    }
}

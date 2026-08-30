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

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * @param crypto   برای کلیدهای نیازمند تأیید بیومتریک (CryptoObject با Signature)
     * @param onSuccess پس از موفقیت سنسور بیومتریک
     * @param onError   خطای غیرقابل بازیابی یا انصراف کاربر
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

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        if (crypto != null) prompt.authenticate(info, crypto) else prompt.authenticate(info)
    }
}

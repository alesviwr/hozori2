package com.smartattendance.security.biometric

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject

/**
 * کلید امضای EC-P256 محافظت‌شده با بیومتریک:
 * • کلید خصوصی هرگز از Android Keystore خارج نمی‌شود
 * • استفاده از کلید فقط پس از تأیید اثر انگشت (CryptoObject) ممکن است
 * • فقط کلید عمومی برای سرور ارسال می‌شود
 */
class BiometricKeyManager @Inject constructor() {

    fun hasKey(): Boolean = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        ks.containsAlias(ALIAS) && ks.getEntry(ALIAS, null) is KeyStore.PrivateKeyEntry
    }.getOrDefault(false)

    fun generateKeyPair(): Boolean = runCatching {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        generator.generateKeyPair()
        true
    }.getOrDefault(false)

    fun newSigningSignature(): Signature? = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        entry?.let {
            Signature.getInstance("SHA256withECDSA").apply { initSign(it.privateKey) }
        }
    }.getOrNull()

    fun publicKeyBase64(): String? = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
        entry?.certificate?.publicKey?.encoded?.let {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "smart_attendance_biometric_key"
    }
}

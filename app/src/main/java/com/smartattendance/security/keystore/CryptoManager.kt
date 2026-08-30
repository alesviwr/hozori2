package com.smartattendance.security.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * رمزنگاری AES-256-GCM با کلید داخل Android Keystore.
 *
 * چرا از EncryptedSharedPreferences استفاده نشد؟
 * کتابخانه androidx.security:security-crypto deprecated شده است؛
 * این پیاده‌سازی مستقیم همان تضمین‌ها (کلید سخت‌افزاری + IV تصادفی + GCM) را فراهم می‌کند
 * و Token نشست، اطلاعات کاربر و Device ID را به‌صورت رمزشده نگه می‌دارد.
 */
class CryptoManager(private val keyAlias: String = "smart_attendance_master_key") {

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** خروجی: Base64(IV[12B] + CipherText+Tag) */
    fun encrypt(plainText: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText)
        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): ByteArray {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = data.copyOfRange(0, IV_SIZE)
        val cipherText = data.copyOfRange(IV_SIZE, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptString(value: String): String = encrypt(value.toByteArray(Charsets.UTF_8))

    fun decryptString(value: String): String = String(decrypt(value), Charsets.UTF_8)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}

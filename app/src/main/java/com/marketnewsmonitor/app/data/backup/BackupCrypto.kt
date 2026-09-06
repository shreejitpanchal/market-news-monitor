package com.marketnewsmonitor.app.data.backup

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for the whole exported backup file. Deliberately
 * NOT tied to an Android Keystore key: a Keystore-backed key is wiped on
 * uninstall and never leaves the device, so it can't decrypt a backup after
 * a reinstall or on a different phone — exactly the "restore the
 * application" scenario export/import exists for. AES-256-GCM is
 * authenticated encryption, so a wrong password or a tampered file both
 * fail loudly (`AEADBadTagException`, a `GeneralSecurityException`) rather
 * than silently producing garbage.
 */
object BackupCrypto {
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12

    // OWASP's 2023 baseline for PBKDF2-HMAC-SHA256.
    private const val ITERATIONS = 210_000

    fun encrypt(plaintext: ByteArray, password: String): EncryptedBackupEnvelope {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedBackupEnvelope(
            iterations = ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    /** @throws java.security.GeneralSecurityException if the password is wrong or the file was tampered with. */
    fun decrypt(envelope: EncryptedBackupEnvelope, password: String): ByteArray {
        val salt = Base64.getDecoder().decode(envelope.salt)
        val iv = Base64.getDecoder().decode(envelope.iv)
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertext)
        val key = deriveKey(password, salt, envelope.iterations)

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val secret = SecretKeyFactory.getInstance(KEY_ALGORITHM).generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}

/** The file written to disk on export — itself plain JSON, wrapping an opaque encrypted payload. */
@Serializable
data class EncryptedBackupEnvelope(
    val format: String = FORMAT_ID,
    val iterations: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
) {
    companion object {
        const val FORMAT_ID = "market-news-monitor-encrypted-backup-v1"
    }
}

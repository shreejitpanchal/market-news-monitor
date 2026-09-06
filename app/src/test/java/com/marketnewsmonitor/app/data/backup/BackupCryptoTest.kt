package com.marketnewsmonitor.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.GeneralSecurityException

class BackupCryptoTest {

    @Test
    fun `decrypts with the correct password back to the original bytes`() {
        val plaintext = """{"tickers":[]}""".encodeToByteArray()

        val envelope = BackupCrypto.encrypt(plaintext, password = "correct horse battery staple")
        val decrypted = BackupCrypto.decrypt(envelope, password = "correct horse battery staple")

        assertEquals(plaintext.decodeToString(), decrypted.decodeToString())
    }

    @Test(expected = GeneralSecurityException::class)
    fun `fails to decrypt with the wrong password`() {
        val envelope = BackupCrypto.encrypt("secret data".encodeToByteArray(), password = "correct password")

        BackupCrypto.decrypt(envelope, password = "wrong password")
    }

    @Test(expected = GeneralSecurityException::class)
    fun `fails to decrypt when the ciphertext has been tampered with`() {
        val envelope = BackupCrypto.encrypt("secret data".encodeToByteArray(), password = "hunter2")
        val tampered = envelope.copy(ciphertext = envelope.ciphertext.dropLast(4) + "AAAA")

        BackupCrypto.decrypt(tampered, password = "hunter2")
    }

    @Test
    fun `uses a fresh random salt and iv on every call`() {
        val first = BackupCrypto.encrypt("same plaintext".encodeToByteArray(), password = "same password")
        val second = BackupCrypto.encrypt("same plaintext".encodeToByteArray(), password = "same password")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.iv, second.iv)
        assertNotEquals(first.ciphertext, second.ciphertext)
    }

    @Test
    fun `does not leave the plaintext recognizable in the ciphertext`() {
        val plaintext = "sk-ant-super-secret-api-key".encodeToByteArray()

        val envelope = BackupCrypto.encrypt(plaintext, password = "a password")

        assertFalse(envelope.ciphertext.contains("sk-ant-super-secret-api-key"))
    }
}

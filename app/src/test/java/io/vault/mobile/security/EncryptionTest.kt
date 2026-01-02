package io.vault.mobile.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.spec.SecretKeySpec

class EncryptionTest {

    @Test
    fun testKeyDerivation() {
        val password = "secret_password"
        val salt = KeyDerivation.generateSalt()
        val key1 = KeyDerivation.deriveKey(password, salt)
        val key2 = KeyDerivation.deriveKey(password, salt)
        
        assertEquals(java.util.Base64.getEncoder().encodeToString(key1), 
                     java.util.Base64.getEncoder().encodeToString(key2))
                     
        val salt2 = KeyDerivation.generateSalt()
        val key3 = KeyDerivation.deriveKey(password, salt2)
        assertNotEquals(java.util.Base64.getEncoder().encodeToString(key1), 
                        java.util.Base64.getEncoder().encodeToString(key3))
    }

    @Test
    fun testEncryptionWithPadding() {
        val testKey = SecretKeySpec(ByteArray(32), "AES")
        val service = EncryptionService()
        
        val originalData = "Hello, world!".toByteArray()
        val inputStream = ByteArrayInputStream(originalData)
        val encryptedOutput = ByteArrayOutputStream()
        
        // 1. Encrypt
        service.encrypt(inputStream, encryptedOutput, originalData.size.toLong(), testKey)
        val encryptedBytes = encryptedOutput.toByteArray()
        
        // 2. Verify Padding (32KB boundary)
        // Format: 1 (IV size) + 12 (IV) + 8 (size) + Data + Padding + 16 (Tag)
        // Header + Data + Padding = 32768
        // Total = 1 + 12 + 32768 + 16 = 32797
        assertEquals(32797, encryptedBytes.size)
        
        // 3. Decrypt
        val decryptedOutput = ByteArrayOutputStream()
        service.decrypt(ByteArrayInputStream(encryptedBytes), decryptedOutput, testKey)
        
        assertEquals("Hello, world!", decryptedOutput.toByteArray().decodeToString())
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        assertTrue(a != b)
    }
}

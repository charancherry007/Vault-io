package io.vault.mobile.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import io.vault.mobile.security.KeyDerivation

class EncryptionTest {

    // Note: CryptoManager uses AndroidKeyStore which requires an Android environment.
    // For unit tests, we'd typically use a mock or a separate JVM-compatible AES implementation.
    // This is a placeholder for where the security logic verification would live.

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
}

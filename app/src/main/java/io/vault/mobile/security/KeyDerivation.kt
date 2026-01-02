package io.vault.mobile.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object KeyDerivation {

    const val ITERATIONS_V1 = 65536
    const val ITERATIONS_V2 = 310000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS_V2): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun generateSalt(): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return salt
    }
}

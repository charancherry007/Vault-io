package io.vault.mobile.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

class EncryptionService @Inject constructor() {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val KEY_ALIAS = "vault_master_key" // Aligned with CryptoManager
    }

    fun isKeyInitialized(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: throw IllegalStateException("Master Key not initialized. Please set up your vault first.")
    }

    fun encrypt(inputStream: InputStream, outputStream: OutputStream): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        outputStream.write(iv.size)
        outputStream.write(iv)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val ciphertext = cipher.update(buffer, 0, bytesRead)
            if (ciphertext != null) {
                outputStream.write(ciphertext)
            }
        }
        val finalResult = cipher.doFinal()
        if (finalResult != null) {
            outputStream.write(finalResult)
        }
        return iv
    }

    fun decrypt(inputStream: InputStream, outputStream: OutputStream) {
        val ivSize = inputStream.read()
        if (ivSize == -1) throw java.io.IOException("Empty or corrupted encrypted file")
        
        val iv = ByteArray(ivSize)
        var totalRead = 0
        while (totalRead < ivSize) {
            val read = inputStream.read(iv, totalRead, ivSize - totalRead)
            if (read == -1) throw java.io.IOException("Incomplete IV in encrypted file")
            totalRead += read
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val plaintext = cipher.update(buffer, 0, bytesRead)
            if (plaintext != null) {
                outputStream.write(plaintext)
            }
        }
        val finalResult = cipher.doFinal()
        if (finalResult != null) {
            outputStream.write(finalResult)
        }
    }
}

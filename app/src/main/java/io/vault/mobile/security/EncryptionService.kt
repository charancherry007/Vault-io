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

    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    companion object {
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
        private const val KEY_ALIAS = "vault_master_key"
        private const val VERSION_V2: Byte = 0x02
    }

    fun isKeyInitialized(): Boolean {
        return keyStore.containsAlias(KEY_ALIAS)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: throw IllegalStateException("Master Key not initialized. Please set up your vault first.")
    }

    fun encrypt(inputStream: InputStream, outputStream: OutputStream, dataSize: Long, testKey: SecretKey? = null): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, testKey ?: getSecretKey())
        
        val iv = cipher.iv
        outputStream.write(VERSION_V2.toInt())
        outputStream.write(iv.size)
        outputStream.write(iv)

        // 1. Encrypt and write the original size (8 bytes)
        val sizeBuffer = java.nio.ByteBuffer.allocate(8).putLong(dataSize).array()
        val encryptedSize = cipher.update(sizeBuffer)
        if (encryptedSize != null) {
            outputStream.write(encryptedSize)
        }

        // 2. Encrypt and write the data
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val ciphertext = cipher.update(buffer, 0, bytesRead)
            if (ciphertext != null) {
                outputStream.write(ciphertext)
            }
        }

        // 3. Add padding to reach 32KB boundary
        // Header (8) + Data + Padding = Multiple of 32768
        val currentTotalPlaintext = 8 + dataSize
        val paddingSize = (32768 - (currentTotalPlaintext % 32768)).toInt() % 32768
        if (paddingSize > 0) {
            val padding = ByteArray(paddingSize)
            java.security.SecureRandom().nextBytes(padding)
            val encryptedPadding = cipher.update(padding)
            if (encryptedPadding != null) {
                outputStream.write(encryptedPadding)
            }
        }

        val finalResult = cipher.doFinal()
        if (finalResult != null) {
            outputStream.write(finalResult)
        }
        return iv
    }

    fun decrypt(inputStream: InputStream, outputStream: OutputStream, testKey: SecretKey? = null) {
        val firstByte = inputStream.read()
        if (firstByte == -1) throw java.io.IOException("Empty or corrupted encrypted file")

        if (firstByte == VERSION_V2.toInt()) {
            decryptV2(inputStream, outputStream, testKey)
        } else {
            // Treat first byte as ivSize (Legacy V1)
            decryptV1(firstByte, inputStream, outputStream, testKey)
        }
    }

    private fun decryptV1(ivSize: Int, inputStream: InputStream, outputStream: OutputStream, testKey: SecretKey?) {
        val iv = ByteArray(ivSize)
        var totalRead = 0
        while (totalRead < ivSize) {
            val read = inputStream.read(iv, totalRead, ivSize - totalRead)
            if (read == -1) throw java.io.IOException("Incomplete IV in legacy file")
            totalRead += read
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, testKey ?: getSecretKey(), spec)

        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            val decrypted = cipher.update(buffer, 0, bytesRead)
            if (decrypted != null) {
                outputStream.write(decrypted)
            }
        }
        val finalResult = cipher.doFinal()
        if (finalResult != null) {
            outputStream.write(finalResult)
        }
    }

    private fun decryptV2(inputStream: InputStream, outputStream: OutputStream, testKey: SecretKey?) {
        val ivSize = inputStream.read()
        if (ivSize == -1) throw java.io.IOException("Missing IV size in V2 file")
        
        val iv = ByteArray(ivSize)
        var totalRead = 0
        while (totalRead < ivSize) {
            val read = inputStream.read(iv, totalRead, ivSize - totalRead)
            if (read == -1) throw java.io.IOException("Incomplete IV in V2 file")
            totalRead += read
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, testKey ?: getSecretKey(), spec)

        var bytesToOutput: Long = -1 
        val sizeBuffer = ByteArray(8)
        var sizeBytesRead = 0

        val buffer = ByteArray(8192)
        var bytesReadIn: Int
        while (inputStream.read(buffer).also { bytesReadIn = it } != -1) {
            val plaintext = cipher.update(buffer, 0, bytesReadIn)
            if (plaintext != null) {
                var offset = 0
                if (bytesToOutput == -1L) {
                    val toCopy = Math.min(8 - sizeBytesRead, plaintext.size)
                    System.arraycopy(plaintext, 0, sizeBuffer, sizeBytesRead, toCopy)
                    sizeBytesRead += toCopy
                    offset += toCopy
                    
                    if (sizeBytesRead == 8) {
                        bytesToOutput = java.nio.ByteBuffer.wrap(sizeBuffer).getLong()
                    }
                }

                if (bytesToOutput != -1L && offset < plaintext.size) {
                    val remainingData = plaintext.size - offset
                    val canWrite = Math.min(remainingData.toLong(), bytesToOutput)
                    if (canWrite > 0) {
                        outputStream.write(plaintext, offset, canWrite.toInt())
                        bytesToOutput -= canWrite
                        offset += canWrite.toInt()
                    }
                }
            }
        }
        
        val finalResult = cipher.doFinal()
        if (finalResult != null) {
            var offset = 0
            if (bytesToOutput == -1L) {
                 val toCopy = Math.min(8 - sizeBytesRead, finalResult.size)
                 System.arraycopy(finalResult, 0, sizeBuffer, sizeBytesRead, toCopy)
                 sizeBytesRead += toCopy
                 offset += toCopy
                 if (sizeBytesRead == 8) {
                     bytesToOutput = java.nio.ByteBuffer.wrap(sizeBuffer).getLong()
                 }
            }
            
            if (bytesToOutput != -1L && offset < finalResult.size) {
                val remainingData = finalResult.size - offset
                val canWrite = Math.min(remainingData.toLong(), bytesToOutput)
                if (canWrite > 0) {
                    outputStream.write(finalResult, offset, canWrite.toInt())
                    bytesToOutput -= canWrite
                }
            }
        }
    }
}

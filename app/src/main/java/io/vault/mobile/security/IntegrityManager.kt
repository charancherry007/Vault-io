package io.vault.mobile.security

import android.content.Context
import android.util.Base64
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val integrityManager = IntegrityManagerFactory.create(context)
    private val secureRandom = SecureRandom()

    /**
     * Requests an integrity token from Google Play Services.
     * The cloudProjectNumber should be your Google Cloud project number.
     * In a real app, the nonce should be generated on the server and verified there.
     * For this local-first app, we generate a local nonce to demonstrate the flow.
     */
    suspend fun requestIntegrityToken(cloudProjectNumber: Long): Result<String> {
        return try {
            val nonce = generateNonce()
            val integrityTokenRequest = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .setNonce(nonce)
                .build()

            val integrityTokenResponse: IntegrityTokenResponse = integrityManager
                .requestIntegrityToken(integrityTokenRequest)
                .await()

            Result.success(integrityTokenResponse.token())
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun generateNonce(): String {
        val nonceBytes = ByteArray(32)
        secureRandom.nextBytes(nonceBytes)
        return Base64.encodeToString(nonceBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}

package io.vault.mobile.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PinataApi {

    @Multipart
    @POST("pinning/pinFileToIPFS")
    suspend fun uploadFile(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<PinataResponse>
}

data class PinataResponse(
    val IpfsHash: String,
    val PinSize: Long,
    val Timestamp: String
)

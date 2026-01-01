package io.vault.mobile.di

import io.vault.mobile.data.remote.PinataApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.vault-io.dummy/") // Placeholder since Pinata is gone
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}

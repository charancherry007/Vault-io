package io.vault.mobile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.vault.mobile.blockchain.ChainGatewayManager
import io.vault.mobile.blockchain.IWalletManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BlockchainModule {

    @Binds
    @Singleton
    abstract fun bindWalletManager(
        gatewayManager: ChainGatewayManager
    ): IWalletManager
}

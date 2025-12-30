package io.vault.mobile.blockchain

import kotlinx.coroutines.flow.Flow

enum class BlockchainType {
    SOLANA
}

interface IWalletManager {
    val type: BlockchainType
    val isMainnet: Boolean
    
    fun getNetworkName(): String
    fun getCurrencySymbol(): String
    fun isLoggedIn(): Boolean
    
    fun login(onSuccess: (String) -> Unit, onError: (String) -> Unit)
    fun getBalance(): Flow<Double>
    fun logout()
}

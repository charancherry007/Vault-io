package io.vault.mobile.blockchain

import io.vault.mobile.BuildConfig
import io.vault.mobile.data.local.dao.RewardDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolanaWalletManager @Inject constructor(
    private val rewardDao: RewardDao
) : IWalletManager {
    
    override val type: BlockchainType = BlockchainType.SOLANA
    override val isMainnet: Boolean = BuildConfig.PROD_WALLET

    private var walletAddress: String? = null

    override fun getNetworkName(): String = if (isMainnet) "Solana Mainnet" else "Solana Devnet"
    override fun getCurrencySymbol(): String = "SOL"

    override fun isLoggedIn(): Boolean = walletAddress != null

    override fun login(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        // In a real SMS implementation, this would trigger the Mobile Wallet Adapter
        // For now, we simulate the connection for your Cyber UI
        walletAddress = "CyberSolAddress111111111111111111111111111"
        onSuccess(walletAddress!!)
    }

    override fun getBalance(): Flow<Double> {
        // In production, this would call the Solana RPC
        // For your app logic, it reflects the earned rewards
        return rewardDao.getTotalByCurrency("SOL").map { it ?: 0.0 }
    }

    override fun logout() {
        walletAddress = null
    }
}

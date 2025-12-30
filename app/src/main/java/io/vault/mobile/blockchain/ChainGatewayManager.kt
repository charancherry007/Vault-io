package io.vault.mobile.blockchain

import io.vault.mobile.data.local.PreferenceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChainGatewayManager @Inject constructor(
    private val solanaWalletManager: SolanaWalletManager,
    private val preferenceManager: PreferenceManager
) : IWalletManager {

    private val activeChain = MutableStateFlow<BlockchainType>(BlockchainType.SOLANA)

    private fun getActiveManager(): IWalletManager = solanaWalletManager

    override val type: BlockchainType get() = getActiveManager().type
    override val isMainnet: Boolean get() = getActiveManager().isMainnet

    override fun getNetworkName(): String = getActiveManager().getNetworkName()
    override fun getCurrencySymbol(): String = getActiveManager().getCurrencySymbol()
    override fun isLoggedIn(): Boolean = getActiveManager().isLoggedIn()

    override fun login(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        getActiveManager().login(onSuccess, onError)
    }

    override fun getBalance(): Flow<Double> {
        return solanaWalletManager.getBalance()
    }

    override fun logout() {
        getActiveManager().logout()
    }
}

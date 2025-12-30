package io.vault.mobile.blockchain

import io.vault.mobile.data.local.dao.RewardDao
import io.vault.mobile.data.local.entity.RewardEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardManager @Inject constructor(
    private val walletManager: IWalletManager,
    private val rewardDao: RewardDao
) {
    sealed class Action {
        object DailyUsage : Action()
        object BackupComplete : Action()
        object SecurityEnable : Action()
        object Withdrawal : Action()
    }

    suspend fun canClaim(action: Action, metadata: String? = null): Boolean {
        val currency = walletManager.getCurrencySymbol()
        return when (action) {
            is Action.DailyUsage -> {
                val lastTime = rewardDao.getLastRewardTime("DailyUsage", currency) ?: 0L
                val diff = System.currentTimeMillis() - lastTime
                diff > 24 * 60 * 60 * 1000 // 24 hours
            }
            is Action.SecurityEnable -> {
                rewardDao.getLastRewardTime("SecurityEnable", currency) == null
            }
            is Action.BackupComplete -> {
                val lastTime = rewardDao.getLastRewardTime("BackupComplete", currency) ?: 0L
                val diff = System.currentTimeMillis() - lastTime
                diff > 7 * 24 * 60 * 60 * 1000 // 7 days
            }
            is Action.Withdrawal -> false // Withdrawal is not a claimable mission
        }
    }

    suspend fun claimReward(action: Action, metadata: String? = null, onResult: (Boolean, String) -> Unit) {
        if (!canClaim(action, metadata)) {
            onResult(false, "Reward already claimed or cooldown active.")
            return
        }

        val amount = when (action) {
            is Action.DailyUsage -> 0.0001
            is Action.SecurityEnable -> 0.0005
            is Action.BackupComplete -> 0.01
            is Action.Withdrawal -> 0.0 
        }

        val reward = RewardEntity(
            actionType = action.javaClass.simpleName,
            amount = amount,
            currency = walletManager.getCurrencySymbol(),
            metadata = metadata
        )

        rewardDao.insertReward(reward)
        onResult(true, "$amount ${walletManager.getCurrencySymbol()} rewarded for ${action.javaClass.simpleName}!")
    }

    suspend fun withdraw(address: String, amount: Double, onResult: (Boolean, String) -> Unit) {
        val currency = walletManager.getCurrencySymbol()
        val currentBalance = rewardDao.getTotalByCurrency(currency).first() ?: 0.0
        if (currentBalance < amount) {
            onResult(false, "Insufficient balance.")
            return
        }

        // Validate address based on currency
        val isValid = currency == "SOL" && address.length >= 32

        if (!isValid) {
            onResult(false, "Invalid $currency address for withdrawal.")
            return
        }

        val withdrawal = RewardEntity(
            actionType = "Withdrawal",
            amount = -amount,
            currency = currency,
            metadata = address
        )

        rewardDao.insertReward(withdrawal)
        onResult(true, "Successfully initiated withdrawal of $amount $currency to $address")
    }
}

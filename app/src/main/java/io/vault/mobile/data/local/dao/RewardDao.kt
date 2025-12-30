package io.vault.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import io.vault.mobile.data.local.entity.RewardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    @Query("SELECT * FROM reward_history WHERE currency = :currency ORDER BY timestamp DESC")
    fun getAllRewardsByCurrency(currency: String): Flow<List<RewardEntity>>

    @Query("SELECT * FROM reward_history WHERE actionType = :actionType AND currency = :currency ORDER BY timestamp DESC")
    fun getRewardsByAction(actionType: String, currency: String): Flow<List<RewardEntity>>

    @Query("SELECT SUM(amount) FROM reward_history WHERE currency = :currency")
    fun getTotalByCurrency(currency: String): Flow<Double?>

    @Query("SELECT MAX(timestamp) FROM reward_history WHERE actionType = :actionType AND currency = :currency")
    suspend fun getLastRewardTime(actionType: String, currency: String): Long?

    @Query("SELECT COUNT(*) FROM reward_history WHERE actionType = :actionType AND metadata = :metadata AND currency = :currency")
    suspend fun countRewardsWithMetadata(actionType: String, metadata: String, currency: String): Int

    @Insert
    suspend fun insertReward(reward: RewardEntity)
}

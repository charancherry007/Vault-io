package io.vault.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reward_history")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // DailyUsage, BackupComplete, SecurityEnable
    val amount: Double,
    val currency: String = "PI", // PI, SOL, TON
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: String? = null // CID or other relevant data
)

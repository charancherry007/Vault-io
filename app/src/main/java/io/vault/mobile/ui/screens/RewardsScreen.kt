package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.blockchain.IWalletManager
import io.vault.mobile.blockchain.RewardManager
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.NeonPurple
import io.vault.mobile.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    walletManager: IWalletManager,
    rewardManager: RewardManager
) {
    val balance by walletManager.getBalance().collectAsState(initial = 0.0)
    var lastRewardMessage by remember { mutableStateOf<String?>(null) }
    var withdrawAddress by remember { mutableStateOf("") }
    var withdrawAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "${walletManager.getCurrencySymbol()} REWARDS",
            fontSize = 20.sp,
            letterSpacing = 2.sp
        )

        // Connection Status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (walletManager.isLoggedIn()) Color.Green else Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (walletManager.isLoggedIn()) "CONNECTED TO ${walletManager.getNetworkName().uppercase()}" else "DISCONNECTED",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Balance Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(NeonPurple.copy(alpha = 0.8f), Color.Black)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AVAILABLE BALANCE", color = Color.LightGray, fontSize = 12.sp)
                Text(
                    "${String.format("%.4f", balance)} ${walletManager.getCurrencySymbol()}",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            "ACTIVE MISSIONS",
            color = NeonBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        RewardMissionItem(
            title = "Daily Vault Check-in",
            reward = "0.01 ${walletManager.getCurrencySymbol()}",
            onClaim = {
                scope.launch {
                    rewardManager.claimReward(RewardManager.Action.DailyUsage) { success, msg ->
                        if (success) lastRewardMessage = msg
                        else lastRewardMessage = "⚠️ $msg"
                    }
                }
            }
        )
        
        RewardMissionItem(
            title = "Decentralized Backup",
            reward = "0.10 ${walletManager.getCurrencySymbol()} (Weekly)",
            onClaim = {
                scope.launch {
                    rewardManager.claimReward(RewardManager.Action.BackupComplete) { success, msg ->
                        if (success) lastRewardMessage = msg
                        else lastRewardMessage = "⚠️ $msg"
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Withdrawal Section
        Text(
            "WITHDRAW TO ${walletManager.getNetworkName().uppercase()}",
            color = NeonPurple,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C23)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = withdrawAddress,
                    onValueChange = { withdrawAddress = it },
                    label = { Text("${walletManager.getCurrencySymbol()} Wallet Address", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = withdrawAmount,
                    onValueChange = { withdrawAmount = it },
                    label = { Text("Amount (${walletManager.getCurrencySymbol()})", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = {
                        val amount = withdrawAmount.toDoubleOrNull() ?: 0.0
                        scope.launch {
                            rewardManager.withdraw(withdrawAddress, amount) { success, msg ->
                                lastRewardMessage = if (success) "✅ $msg" else "❌ $msg"
                                if (success) {
                                    withdrawAddress = ""
                                    withdrawAmount = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = withdrawAddress.isNotBlank() && withdrawAmount.isNotBlank() && balance > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("INITIATE WITHDRAWAL", fontWeight = FontWeight.Bold)
                }
            }
        }

        lastRewardMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(it, color = NeonBlue, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun RewardMissionItem(title: String, reward: String, onClaim: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C23))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Reward: $reward", color = NeonBlue, fontSize = 12.sp)
            }
            Button(
                onClick = onClaim,
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.2f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue)
            ) {
                Text("CLAIM", color = NeonBlue, fontSize = 12.sp)
            }
        }
    }
}

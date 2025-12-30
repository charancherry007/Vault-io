package io.vault.mobile.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.NeonPurple
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    viewModel: io.vault.mobile.ui.viewmodel.VaultViewModel? = null,
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPage("Zero-Knowledge", "All your data is encrypted. We never see your master password.", NeonBlue),
        OnboardingPage("Decentralized Backup", "Backup your encrypted vault. No central servers, total ownership.", NeonPurple),
        OnboardingPage("SOL Integration", "Earn SOL for maintaining a secure vault and performing backups.", NeonBlue)
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { pageIndex ->
            val page = pages[pageIndex]
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(120.dp).clip(CircleShape).background(page.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Icon placeholder
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(page.title, color = page.color, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(page.description, color = Color.Gray, textAlign = TextAlign.Center, fontSize = 16.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicators
            Row {
                repeat(3) { index ->
                    val color = if (pagerState.currentPage == index) NeonBlue else Color.DarkGray
                    Box(modifier = Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
                }
            }

            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        viewModel?.setOnboardingCompleted()
                        onFinished()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
            ) {
                Text(if (pagerState.currentPage == 2) "GET STARTED" else "NEXT", color = Color.Black)
            }
        }
    }
}

data class OnboardingPage(val title: String, val description: String, val color: Color)

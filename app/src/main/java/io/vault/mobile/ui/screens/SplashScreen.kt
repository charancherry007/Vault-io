package io.vault.mobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.ui.theme.NeonBlue
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1.2f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "VAULT",
                style = androidx.compose.ui.text.TextStyle(
                    color = NeonBlue,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 12.sp
                ),
                modifier = Modifier.scale(scale.value)
            )
            Text(
                "DECENTRALIZED & SECURE",
                color = Color.Gray,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

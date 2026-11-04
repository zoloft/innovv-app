package com.zoloft.innovv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvTheme

@Composable
fun App() {
    InnovvTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(InnovvColors.BackgroundPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text("INNOVV Open Cam", color = InnovvColors.TextPrimary)
        }
    }
}

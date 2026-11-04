package com.zoloft.innovv.app.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoloft.innovv.app.resources.Res
import com.zoloft.innovv.app.resources.inter_bold
import com.zoloft.innovv.app.resources.inter_medium
import com.zoloft.innovv.app.resources.inter_regular
import com.zoloft.innovv.app.resources.inter_semibold
import org.jetbrains.compose.resources.Font

object InnovvColors {
    val BackgroundPrimary = Color(0xFF111114)
    val SurfaceCard = Color(0xFF1C1D24)
    val AccentPrimary = Color(0xFFFAAF00)
    val AccentPrimaryDark = Color(0xFFB87000)
    val SemanticSuccess = Color(0xFF21AE61)
    val SemanticDanger = Color(0xFFD9382B)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFF9CA0A9)
    val BorderSubtle = Color(0xFF2E3138)

    val SuccessPill = SemanticSuccess.copy(alpha = 0.25f)
    val DangerPill = SemanticDanger.copy(alpha = 0.35f)
    val SuccessBadge = SemanticSuccess.copy(alpha = 0.30f)
    val DangerBadge = SemanticDanger.copy(alpha = 0.30f)
    val AccentBadge = AccentPrimary.copy(alpha = 0.30f)
}

object InnovvDimens {
    val screenPadding = 20.dp
    val sectionGap = 20.dp
    val cardGap = 10.dp
    val innerPadding = 12.dp
    val liveCardRadius = 16.dp
    val quickActionRadius = 16.dp
    val telemetryRadius = 12.dp
    val mediaRowRadius = 12.dp
    val settingsRowRadius = 10.dp
    val videoRadius = 10.dp
    val segmentedRadius = 8.dp
    val pillRadius = 100.dp
}

private val InnovvColorScheme: ColorScheme = darkColorScheme(
    primary = InnovvColors.AccentPrimary,
    onPrimary = InnovvColors.BackgroundPrimary,
    secondary = InnovvColors.SemanticSuccess,
    onSecondary = InnovvColors.BackgroundPrimary,
    error = InnovvColors.SemanticDanger,
    onError = InnovvColors.TextPrimary,
    background = InnovvColors.BackgroundPrimary,
    onBackground = InnovvColors.TextPrimary,
    surface = InnovvColors.SurfaceCard,
    onSurface = InnovvColors.TextPrimary,
    surfaceVariant = InnovvColors.SurfaceCard,
    onSurfaceVariant = InnovvColors.TextSecondary,
    outline = InnovvColors.BorderSubtle,
)

@Composable
fun InnovvTheme(content: @Composable () -> Unit) {
    val interFamily = FontFamily(
        Font(Res.font.inter_regular, FontWeight.Normal),
        Font(Res.font.inter_medium, FontWeight.Medium),
        Font(Res.font.inter_semibold, FontWeight.SemiBold),
        Font(Res.font.inter_bold, FontWeight.Bold),
    )

    val typography = androidx.compose.material3.Typography(
        displayLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
        headlineLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp),
        headlineMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        titleSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        bodyLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
        bodyMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
        bodySmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp),
        labelLarge = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp),
        labelMedium = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
        labelSmall = TextStyle(fontFamily = interFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
    )

    MaterialTheme(
        colorScheme = InnovvColorScheme,
        typography = typography,
        content = content,
    )
}

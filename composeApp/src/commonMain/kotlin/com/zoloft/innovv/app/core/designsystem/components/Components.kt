package com.zoloft.innovv.app.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvDimens
import com.zoloft.innovv.app.domain.model.Banner
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.ClipCategory
import kotlin.math.sin

@Composable
fun InnovvSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InnovvColors.TextSecondary,
    fontSize: Int = 13,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
}

@Composable
fun ConnectionStatusPill(
    state: CameraConnectionState,
    modifier: Modifier = Modifier,
) {
    val (label, background, foreground) = when (state) {
        is CameraConnectionState.Connected -> Triple(
            "● Connected (${state.ssid})",
            InnovvColors.SuccessPill,
            InnovvColors.SemanticSuccess,
        )
        is CameraConnectionState.Connecting -> Triple(
            "◌ Connecting to ${state.ssid}...",
            InnovvColors.AccentPrimary.copy(alpha = 0.25f),
            InnovvColors.AccentPrimary,
        )
        is CameraConnectionState.Reconnecting -> Triple(
            "◌ Reconnecting (attempt ${state.attempt})...",
            InnovvColors.AccentPrimary.copy(alpha = 0.25f),
            InnovvColors.AccentPrimary,
        )
        is CameraConnectionState.Scanning -> Triple(
            "◌ Scanning for camera...",
            InnovvColors.AccentPrimary.copy(alpha = 0.25f),
            InnovvColors.AccentPrimary,
        )
        is CameraConnectionState.Failed -> Triple(
            "● Connection failed",
            InnovvColors.DangerPill,
            InnovvColors.TextPrimary,
        )
        CameraConnectionState.Disconnected -> Triple(
            "● Not Connected",
            InnovvColors.DangerPill,
            InnovvColors.TextPrimary,
        )
    }

    val pulse = if (state is CameraConnectionState.Connecting) {
        val transition = rememberInfiniteTransition(label = "connecting-pulse")
        val alpha by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "connecting-pulse-alpha",
        )
        alpha
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(pulse),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class CapsuleStyle { Amber, Surface, Danger, Ghost }

@Composable
fun CapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CapsuleStyle = CapsuleStyle.Amber,
    enabled: Boolean = true,
) {
    val background = when (style) {
        CapsuleStyle.Amber -> InnovvColors.AccentPrimary
        CapsuleStyle.Surface -> InnovvColors.SurfaceCard
        CapsuleStyle.Danger -> InnovvColors.SemanticDanger
        CapsuleStyle.Ghost -> Color.Transparent
    }
    val foreground = when (style) {
        CapsuleStyle.Amber -> InnovvColors.BackgroundPrimary
        CapsuleStyle.Surface -> InnovvColors.TextPrimary
        CapsuleStyle.Danger -> InnovvColors.TextPrimary
        CapsuleStyle.Ghost -> InnovvColors.TextSecondary
    }
    Box(
        modifier = modifier
            .height(39.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(InnovvDimens.segmentedRadius))
            .background(InnovvColors.BackgroundPrimary)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (selected) InnovvColors.AccentPrimary else Color.Transparent,
                animationSpec = tween(120),
                label = "segment-background-$index",
            )
            val foreground by animateColorAsState(
                targetValue = if (selected) InnovvColors.BackgroundPrimary else InnovvColors.TextSecondary,
                animationSpec = tween(120),
                label = "segment-foreground-$index",
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = foreground,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun TelemetryCard(
    label: String,
    value: String,
    subtext: String,
    valueColor: Color = InnovvColors.TextPrimary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(InnovvDimens.telemetryRadius))
            .background(InnovvColors.SurfaceCard)
            .padding(InnovvDimens.innerPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = InnovvColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = value,
                color = valueColor,
                fontSize = if (value.length > 9) 13.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
        Text(
            text = subtext,
            color = InnovvColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun QuickActionTile(
    icon: ImageVector,
    label: String,
    background: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(68.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(InnovvDimens.quickActionRadius))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CategoryBadge(
    category: ClipCategory,
    modifier: Modifier = Modifier,
) {
    val background = when (category) {
        ClipCategory.LOOP -> InnovvColors.SuccessBadge
        ClipCategory.PROTECTED -> InnovvColors.DangerBadge
        ClipCategory.PARKED -> InnovvColors.AccentBadge
        ClipCategory.PHOTO -> InnovvColors.AccentBadge
    }
    val foreground = when (category) {
        ClipCategory.LOOP -> InnovvColors.SemanticSuccess
        ClipCategory.PROTECTED -> InnovvColors.SemanticDanger
        ClipCategory.PARKED, ClipCategory.PHOTO -> InnovvColors.AccentPrimary
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = category.label,
            color = foreground,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SettingsRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    valueColor: Color = InnovvColors.SemanticSuccess,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnovvDimens.settingsRowRadius))
            .background(InnovvColors.SurfaceCard)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(InnovvDimens.innerPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = if (enabled) InnovvColors.TextPrimary else InnovvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = InnovvColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (locked) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Requires camera connection",
                    tint = InnovvColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = value,
                color = if (enabled) valueColor else InnovvColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SettingsSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnovvDimens.settingsRowRadius))
            .background(InnovvColors.SurfaceCard)
            .padding(InnovvDimens.innerPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) InnovvColors.TextPrimary else InnovvColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        SegmentedPill(
            options = options,
            selectedIndex = selectedIndex,
            onSelect = { if (enabled) onSelect(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun DashboardTopBar(
    title: String,
    connection: CameraConnectionState,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onBack != null) {
                    Text(
                        text = "‹",
                        color = InnovvColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onBack),
                    )
                }
                Text(
                    text = title,
                    color = InnovvColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
            }
            ConnectionStatusPill(connection)
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = "Settings",
                tint = InnovvColors.TextPrimary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun BannerBar(
    banner: Banner,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (background, foreground, text) = when (banner) {
        is Banner.Info -> Triple(
            InnovvColors.SemanticSuccess.copy(alpha = 0.16f),
            InnovvColors.SemanticSuccess,
            banner.message,
        )
        is Banner.Error -> Triple(
            InnovvColors.SemanticDanger.copy(alpha = 0.18f),
            InnovvColors.SemanticDanger,
            banner.message,
        )
        is Banner.RecordingPaused -> Triple(
            InnovvColors.AccentPrimary.copy(alpha = 0.16f),
            InnovvColors.AccentPrimary,
            banner.message,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onDismiss != null) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = foreground,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

@Composable
fun GForceWaveform(
    modifier: Modifier = Modifier,
    color: Color = InnovvColors.SemanticSuccess,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val path = Path()
        val points = 24
        for (i in 0..points) {
            val x = w * i / points
            val y = mid + sin(i * 0.9f) * (h * 0.22f) + sin(i * 0.37f) * (h * 0.10f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
        )
        drawLine(
            color = color.copy(alpha = 0.25f),
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 1f,
        )
    }
}

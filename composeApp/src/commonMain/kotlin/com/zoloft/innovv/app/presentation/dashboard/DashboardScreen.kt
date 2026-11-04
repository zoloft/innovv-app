package com.zoloft.innovv.app.presentation.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvDimens
import com.zoloft.innovv.app.core.designsystem.components.BannerBar
import com.zoloft.innovv.app.core.designsystem.components.CapsuleButton
import com.zoloft.innovv.app.core.designsystem.components.CapsuleStyle
import com.zoloft.innovv.app.core.designsystem.components.ConnectionStatusPill
import com.zoloft.innovv.app.core.designsystem.components.DashboardTopBar
import com.zoloft.innovv.app.core.designsystem.components.GForceWaveform
import com.zoloft.innovv.app.core.designsystem.components.InnovvSectionHeader
import com.zoloft.innovv.app.core.designsystem.components.QuickActionTile
import com.zoloft.innovv.app.core.designsystem.components.SegmentedPill
import com.zoloft.innovv.app.core.designsystem.components.TelemetryCard
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.ConnectionStep
import com.zoloft.innovv.app.domain.model.DashboardUiState
import com.zoloft.innovv.app.domain.model.GForceState
import com.zoloft.innovv.app.domain.model.GpsState
import com.zoloft.innovv.app.domain.model.RecordingState
import com.zoloft.innovv.app.domain.model.StorageState
import com.zoloft.innovv.app.platform.PlatformLiveVideo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onGallery: () -> Unit,
    onLockEvent: () -> Unit,
    onSnapshot: () -> Unit,
    onTogglePause: () -> Unit,
    onChannel: (CameraChannel) -> Unit,
    onDismissBanner: () -> Unit,
    onRetry: () -> Unit,
    onDemo: (() -> Unit)? = null,
    demoMode: Boolean = false,
) {
    var flash by remember { mutableStateOf(false) }
    LaunchedEffect(state.shutterFlashKey) {
        if (state.shutterFlashKey > 0) {
            flash = true
            delay(90)
            flash = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InnovvColors.BackgroundPrimary)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = InnovvDimens.screenPadding, vertical = InnovvDimens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(InnovvDimens.sectionGap),
            ) {
                DashboardTopBar(
                    title = "INNOVV Open-Source Cam",
                    connection = state.connection,
                    onSettings = onSettings,
                )

                if (demoMode) {
                    BannerBar(
                        banner = com.zoloft.innovv.app.domain.model.Banner.Info(
                            "Demo mode — simulated camera data",
                        ),
                        onDismiss = null,
                    )
                }

                state.banner?.let { banner ->
                    BannerBar(banner = banner, onDismiss = onDismissBanner)
                }

                LiveViewCard(
                    state = state,
                    onConnect = onConnect,
                    onCancel = onCancel,
                    onScan = onScan,
                    onRetry = onRetry,
                    onChannel = onChannel,
                    onTogglePause = onTogglePause,
                    onDemo = onDemo,
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InnovvSectionHeader("Telemetry Overlay")
                    TelemetryRow(state)
                }
            }

            QuickActionsSection(
                state = state,
                onGallery = onGallery,
                onLockEvent = onLockEvent,
                onSnapshot = onSnapshot,
                onTogglePause = onTogglePause,
            )
        }

        if (flash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun LiveViewCard(
    state: DashboardUiState,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onScan: () -> Unit,
    onRetry: () -> Unit,
    onChannel: (CameraChannel) -> Unit,
    onTogglePause: () -> Unit,
    onDemo: (() -> Unit)?,
) {
    val connection = state.connection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(InnovvDimens.liveCardRadius))
            .background(InnovvColors.SurfaceCard)
            .padding(InnovvDimens.innerPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (connection) {
            is CameraConnectionState.Connected -> {
                LiveControlBar(
                    state = state,
                    onChannel = onChannel,
                )
                LiveVideoSurface(state = state, onTogglePause = onTogglePause)
            }

            is CameraConnectionState.Connecting -> ConnectingCard(
                connection = connection,
                onCancel = onCancel,
            )

            is CameraConnectionState.Reconnecting -> StatusCard(
                icon = { CircularProgressIndicator(color = InnovvColors.AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp)) },
                title = "Reconnecting to camera",
                body = "Attempt ${connection.attempt}. Keep the camera powered and within Wi-Fi range.",
                actionLabel = "Cancel",
                onAction = onCancel,
            )

            is CameraConnectionState.Scanning -> StatusCard(
                icon = { CircularProgressIndicator(color = InnovvColors.AccentPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp)) },
                title = "Scanning for camera",
                body = "Looking for INNOVV_K7_5.8G on the 5.8 GHz band.",
                actionLabel = "Cancel",
                onAction = onCancel,
            )

            is CameraConnectionState.Failed -> StatusCard(
                icon = { DisconnectedIcon() },
                title = "Connection failed",
                body = connection.failure.userMessage(),
                actionLabel = "Try again",
                onAction = onRetry,
            )

            CameraConnectionState.Disconnected -> DisconnectedCard(
                onConnect = onConnect,
                onScan = onScan,
                onDemo = onDemo,
            )
        }
    }
}

@Composable
private fun LiveControlBar(
    state: DashboardUiState,
    onChannel: (CameraChannel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedPill(
            options = listOf("Front", "Rear"),
            selectedIndex = if (state.activeChannel == CameraChannel.FRONT) 0 else 1,
            onSelect = { onChannel(if (it == 0) CameraChannel.FRONT else CameraChannel.REAR) },
        )
        RecordingPill(state.telemetry.recording)
    }
}

@Composable
private fun RecordingPill(recording: RecordingState) {
    val seconds = (recording as? RecordingState.Recording)?.elapsedSeconds
    val label = if (seconds == null) {
        "● REC 00:00:00"
    } else {
        "● REC ${formatDuration(seconds)}"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(InnovvDimens.pillRadius))
            .background(InnovvColors.SemanticDanger)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = InnovvColors.TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LiveVideoSurface(
    state: DashboardUiState,
    onTogglePause: () -> Unit,
) {
    val paused = state.previewPaused
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(InnovvDimens.videoRadius))
            .background(InnovvColors.BackgroundPrimary),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF15191E),
                        Color(0xFF0B0D10),
                    ),
                ),
            )
            val horizon = size.height * 0.62f
            drawLine(
                color = InnovvColors.BorderSubtle,
                start = Offset(0f, horizon),
                end = Offset(size.width, horizon),
                strokeWidth = 1f,
            )
            repeat(5) { index ->
                val y = horizon + (size.height - horizon) * (index + 1) / 6f
                drawLine(
                    color = InnovvColors.BorderSubtle.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        if (!paused && state.streamUrl != null) {
            PlatformLiveVideo(
                url = state.streamUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (state.streamUrl == null || paused) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (paused) "Preview paused" else "Live 2K RTSP Video Stream",
                    color = InnovvColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (paused) "Recording continues on the camera" else "73 km/h • GPS Lock • XYZ Graph",
                    color = InnovvColors.AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(InnovvColors.BackgroundPrimary.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "● LIVE",
                color = InnovvColors.SemanticDanger,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(InnovvColors.BackgroundPrimary.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "GPS",
                color = InnovvColors.SemanticSuccess,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "5Hz",
                color = InnovvColors.TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "73 km/h",
                color = InnovvColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            GForceWaveform(
                modifier = Modifier.width(74.dp).height(14.dp),
                color = InnovvColors.SemanticSuccess,
            )
        }

        if (paused) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(InnovvColors.AccentPrimary)
                    .clickable(onClick = onTogglePause)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Resume",
                    color = InnovvColors.BackgroundPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DisconnectedCard(
    onConnect: () -> Unit,
    onScan: () -> Unit,
    onDemo: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DisconnectedIcon()
        Text(
            text = "No camera found or offline",
            color = InnovvColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        CapsuleButton(
            text = "Tap to connect",
            onClick = onConnect,
            style = CapsuleStyle.Amber,
        )
        Text(
            text = "or Scan for camera (Wi-Fi 5.8G)",
            color = InnovvColors.SemanticSuccess,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onScan),
        )
        if (onDemo != null) {
            Text(
                text = "or Explore demo mode",
                color = InnovvColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDemo),
            )
        }
    }
}

@Composable
private fun ConnectingCard(
    connection: CameraConnectionState.Connecting,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            color = InnovvColors.AccentPrimary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = connection.step.label(connection.ssid),
            color = InnovvColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Keep the camera powered and close to the phone.",
            color = InnovvColors.TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        if (connection.canCancel) {
            Text(
                text = "Cancel",
                color = InnovvColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onCancel).padding(8.dp),
            )
        }
    }
}

@Composable
private fun StatusCard(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            text = title,
            color = InnovvColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = InnovvColors.TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        CapsuleButton(
            text = actionLabel,
            onClick = onAction,
            style = CapsuleStyle.Surface,
        )
    }
}

@Composable
private fun DisconnectedIcon() {
    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.PhotoCamera,
            contentDescription = null,
            tint = InnovvColors.TextSecondary,
            modifier = Modifier.size(34.dp),
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            tint = InnovvColors.SemanticDanger,
            modifier = Modifier.align(Alignment.CenterEnd).size(22.dp),
        )
    }
}

@Composable
private fun TelemetryRow(state: DashboardUiState) {
    val connected = state.isConnected
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(InnovvDimens.cardGap),
    ) {
        val gps = state.telemetry.gps
        TelemetryCard(
            label = "GPS Signal",
            value = when (gps) {
                is GpsState.Locked -> "${gps.rateHz}Hz"
                GpsState.Searching -> "Searching..."
                GpsState.Disabled -> "Disabled"
                GpsState.Unavailable -> "No GPS"
            },
            subtext = when (gps) {
                is GpsState.Locked -> "Locked • ${gps.satellites} sats"
                GpsState.Searching -> "--"
                GpsState.Disabled -> "Disabled"
                GpsState.Unavailable -> "Unavailable"
            },
            valueColor = if (gps is GpsState.Locked) InnovvColors.SemanticSuccess else InnovvColors.TextPrimary,
            enabled = connected,
            modifier = Modifier.weight(1f),
        )

        val gForce = state.telemetry.gForce
        TelemetryCard(
            label = "3-Axis G-Force",
            value = when (gForce) {
                is GForceState.Live -> "${formatG(gForce.magnitudeG)} G"
                GForceState.Idle -> "Idle"
                GForceState.Unavailable -> "Idle"
            },
            subtext = when (gForce) {
                is GForceState.Live -> "Calibrated"
                GForceState.Idle, GForceState.Unavailable -> "Z - X - Y -"
            },
            enabled = connected,
            modifier = Modifier.weight(1f),
            trailing = if (gForce is GForceState.Live) {
                { GForceWaveform(Modifier.width(26.dp).height(12.dp), InnovvColors.SemanticSuccess) }
            } else {
                null
            },
        )

        val storage = state.telemetry.storage
        TelemetryCard(
            label = "Storage Health",
            value = when (storage) {
                is StorageState.Ready -> if (storage.usedBytes != null && storage.totalBytes != null) {
                    "${storage.usedBytes.asGigabytes()} / ${storage.totalBytes.asGigabytes()}G"
                } else {
                    "${storage.freeBytes.asGigabytes()}G free"
                }
                StorageState.NoCard -> "No SD"
                StorageState.CardError -> "SD Error"
                StorageState.Unknown -> "No SD"
            },
            subtext = when (storage) {
                is StorageState.Ready -> "SD Card OK"
                StorageState.NoCard -> "Disconnected"
                StorageState.CardError -> "Check card"
                StorageState.Unknown -> "Disconnected"
            },
            valueColor = InnovvColors.AccentPrimary,
            enabled = connected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionsSection(
    state: DashboardUiState,
    onGallery: () -> Unit,
    onLockEvent: () -> Unit,
    onSnapshot: () -> Unit,
    onTogglePause: () -> Unit,
) {
    val actions = state.quickActions
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InnovvColors.BackgroundPrimary)
            .padding(horizontal = InnovvDimens.screenPadding, vertical = InnovvDimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InnovvSectionHeader(
            text = if (state.isConnected) "Bottom Quick Actions" else "Bottom Quick Actions (Disabled)",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(InnovvDimens.cardGap),
        ) {
            QuickActionTile(
                icon = Icons.Rounded.PhotoLibrary,
                label = "Gallery",
                background = InnovvColors.SurfaceCard,
                contentColor = InnovvColors.TextPrimary,
                enabled = actions.galleryEnabled,
                onClick = onGallery,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = Icons.Rounded.WarningAmber,
                label = "Lock Event",
                background = InnovvColors.SemanticDanger,
                contentColor = InnovvColors.TextPrimary,
                enabled = actions.lockEventEnabled,
                onClick = onLockEvent,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = Icons.Rounded.PhotoCamera,
                label = "Snapshot",
                background = InnovvColors.SurfaceCard,
                contentColor = InnovvColors.TextPrimary,
                enabled = actions.snapshotEnabled,
                onClick = onSnapshot,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = if (state.previewPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                label = if (state.previewPaused) "Resume Live" else "Pause Live",
                background = InnovvColors.AccentPrimary,
                contentColor = InnovvColors.BackgroundPrimary,
                enabled = actions.pauseEnabled,
                onClick = onTogglePause,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun ConnectionStep.label(ssid: String): String = when (this) {
    ConnectionStep.JOINING_WIFI -> "Joining Wi-Fi SSID ($ssid)..."
    ConnectionStep.BINDING_NETWORK -> "Binding socket to camera gateway (192.168.1.254)..."
    ConnectionStep.HANDSHAKING -> "Handshaking with the camera..."
    ConnectionStep.BUFFERING_STREAM -> "Buffering 2K RTSP Live Stream..."
}

private fun com.zoloft.innovv.app.domain.model.CameraFailure.userMessage(): String = when (this) {
    com.zoloft.innovv.app.domain.model.CameraFailure.WifiJoinDenied ->
        "Wi-Fi access was denied. Allow the camera network and try again."
    com.zoloft.innovv.app.domain.model.CameraFailure.CameraUnreachable ->
        "The camera is not reachable at 192.168.1.254."
    is com.zoloft.innovv.app.domain.model.CameraFailure.CameraCommand ->
        "The camera rejected the request (status $code)."
    com.zoloft.innovv.app.domain.model.CameraFailure.StreamTimeout ->
        "The live stream did not start. Check the camera and try again."
    is com.zoloft.innovv.app.domain.model.CameraFailure.Unknown -> message
}

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

private fun Long.asGigabytes(): String = ((this.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()).toString()

private fun formatG(value: Float): String {
    val hundredths = (value * 100f).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

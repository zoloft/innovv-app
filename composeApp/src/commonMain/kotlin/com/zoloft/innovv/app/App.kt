package com.zoloft.innovv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zoloft.innovv.app.core.designsystem.InnovvColors
import com.zoloft.innovv.app.core.designsystem.InnovvTheme
import com.zoloft.innovv.app.platform.isDebugBuild
import com.zoloft.innovv.app.presentation.dashboard.DashboardScreen
import com.zoloft.innovv.app.presentation.gallery.FileGalleryScreen
import com.zoloft.innovv.app.presentation.settings.SettingsEvent
import com.zoloft.innovv.app.presentation.settings.SettingsModalSheet

@Composable
fun App() {
    InnovvTheme {
        var demoMode by remember { mutableStateOf(false) }
        val container = remember(demoMode) { AppContainer(useDemo = demoMode) }
        val dashboardState by container.dashboard.state.collectAsStateWithLifecycle()
        val galleryState by container.gallery.state.collectAsStateWithLifecycle()
        val settingsState by container.settings.state.collectAsStateWithLifecycle()
        val debugLogState by container.settings.debugLogState.collectAsStateWithLifecycle()
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var showSettings by remember { mutableStateOf(false) }

        LaunchedEffect(container.settings) {
            container.settings.events.collect { event ->
                when (event) {
                    SettingsEvent.Close -> showSettings = false
                    is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(InnovvColors.BackgroundPrimary),
        ) {
            NavHost(
                navController = navController,
                startDestination = "dashboard",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        state = dashboardState,
                        onConnect = container.dashboard::connect,
                        onCancel = container.dashboard::cancelConnect,
                        onScan = container.dashboard::connect,
                        onOpenWifiSettings = container.dashboard::openWifiSettings,
                        onSettings = { showSettings = true },
                        onGallery = { navController.navigate("gallery") },
                        onLockEvent = container.dashboard::lockEvent,
                        onSnapshot = container.dashboard::captureSnapshot,
                        onTogglePause = container.dashboard::togglePreviewPause,
                        onChannel = container.dashboard::setChannel,
                        onDismissBanner = container.dashboard::dismissBanner,
                        onRetry = container.dashboard::connect,
                        onDemo = if (isDebugBuild) {
                            { demoMode = true }
                        } else {
                            null
                        },
                        demoMode = demoMode,
                        onVideoFrame = container.dashboard::onVideoFrame,
                        onVideoError = container.dashboard::onVideoError,
                        onSelectNetwork = container.dashboard::connectTo,
                    )
                }
                composable("gallery") {
                    FileGalleryScreen(
                        state = galleryState,
                        messages = container.gallery.messages,
                        onLoad = container.gallery::load,
                        onLeave = container.gallery::onLeave,
                        onBack = { navController.popBackStack() },
                        onSettings = { showSettings = true },
                        onRefresh = container.gallery::refresh,
                        onFilter = container.gallery::selectFilter,
                        onToggleSelection = container.gallery::toggleSelection,
                        onClearSelection = container.gallery::clearSelection,
                        onSelectAll = container.gallery::selectAll,
                        onOpenPreview = container.gallery::openPreview,
                        onClosePreview = container.gallery::closePreview,
                        onTogglePlayback = container.gallery::togglePreviewPlayback,
                        onPreviewChannel = container.gallery::setPreviewChannel,
                        onTogglePiP = container.gallery::togglePiP,
                        onSeekPreview = container.gallery::seekPreview,
                        onDownload = container.gallery::downloadSelected,
                        onDelete = container.gallery::deleteSelected,
                        onDismissTransfer = container.gallery::dismissTransfer,
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = InnovvColors.SurfaceCard,
                    contentColor = InnovvColors.TextPrimary,
                )
            }
        }

        if (showSettings) {
            SettingsModalSheet(
                state = settingsState,
                onLoad = container.settings::load,
                onDismiss = {
                    container.settings.discard()
                    showSettings = false
                },
                onUpdateDraft = container.settings::updateDraft,
                onApply = container.settings::apply,
                onFormatSd = container.settings::formatSdCard,
                onRunDiagnostics = container.settings::runDiagnostics,
                onDismissApplyState = container.settings::dismissApplyState,
                onDismissError = container.settings::dismissError,
                debugLogState = debugLogState,
                onToggleDebugLogging = container.settings::setDebugLoggingEnabled,
                onClearDebugLog = container.settings::clearDebugLog,
            )
        }
    }
}

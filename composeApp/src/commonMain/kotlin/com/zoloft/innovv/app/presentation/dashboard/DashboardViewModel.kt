package com.zoloft.innovv.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.model.Banner
import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.DashboardUiState
import com.zoloft.innovv.app.domain.model.HandshakeUiState
import com.zoloft.innovv.app.domain.model.QuickActionsState
import com.zoloft.innovv.app.domain.model.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val camera: CameraController,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            camera.connection.collect { connection ->
                _state.update { current ->
                    current.copy(
                        connection = connection,
                        previewPaused = when (connection) {
                            is CameraConnectionState.Connected ->
                                connection.stream == StreamState.PAUSED_BY_USER ||
                                    connection.stream == StreamState.PAUSED_FOR_GALLERY
                            else -> false
                        },
                        handshake = (connection as? CameraConnectionState.Connecting)?.let {
                            HandshakeUiState(step = it.step, showCancel = it.canCancel)
                        },
                        quickActions = current.quickActions.copy(
                            galleryEnabled = connection is CameraConnectionState.Connected,
                            lockEventEnabled = connection is CameraConnectionState.Connected,
                            snapshotEnabled = connection is CameraConnectionState.Connected,
                            pauseEnabled = connection is CameraConnectionState.Connected,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            camera.telemetry.collect { telemetry ->
                _state.update { it.copy(telemetry = telemetry) }
            }
        }
    }

    fun connect() = camera.connect()

    fun cancelConnect() = camera.cancelConnect()

    fun disconnect() = camera.disconnect()

    fun setChannel(channel: CameraChannel) {
        _state.update { it.copy(activeChannel = channel) }
        viewModelScope.launch { camera.setChannel(channel) }
    }

    fun togglePreviewPause() {
        viewModelScope.launch {
            if (_state.value.previewPaused) {
                camera.resumePreview()
            } else {
                camera.pausePreview()
            }
        }
    }

    fun captureSnapshot() {
        if (_state.value.quickActions.snapshotInFlight) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    quickActions = it.quickActions.copy(snapshotInFlight = true),
                    shutterFlashKey = it.shutterFlashKey + 1,
                )
            }
            val ok = camera.captureSnapshot()
            _state.update {
                it.copy(
                    quickActions = it.quickActions.copy(snapshotInFlight = false),
                    banner = if (ok) {
                        Banner.Info("Snapshot saved on front and rear channels")
                    } else {
                        Banner.Error("Snapshot failed. Check the camera connection.")
                    },
                )
            }
        }
    }

    fun lockEvent() {
        if (_state.value.quickActions.lockEventInFlight) return
        viewModelScope.launch {
            _state.update {
                it.copy(quickActions = it.quickActions.copy(lockEventInFlight = true))
            }
            val ok = camera.lockEvent()
            _state.update {
                it.copy(
                    quickActions = it.quickActions.copy(lockEventInFlight = false),
                    banner = if (ok) {
                        Banner.Info("Current clip protected")
                    } else {
                        Banner.Error("Lock Event is not supported by this firmware.")
                    },
                )
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch { camera.startRecording() }
    }

    fun stopRecording() {
        viewModelScope.launch { camera.stopRecording() }
    }

    fun dismissBanner() {
        _state.update { it.copy(banner = null) }
    }
}

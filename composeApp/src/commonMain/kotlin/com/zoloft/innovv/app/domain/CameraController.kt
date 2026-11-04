package com.zoloft.innovv.app.domain

import com.zoloft.innovv.app.domain.model.CameraChannel
import com.zoloft.innovv.app.domain.model.CameraConnectionState
import com.zoloft.innovv.app.domain.model.TelemetryState
import kotlinx.coroutines.flow.StateFlow

interface CameraController {
    val connection: StateFlow<CameraConnectionState>
    val telemetry: StateFlow<TelemetryState>

    fun connect(ssid: String = DEFAULT_SSID)
    fun cancelConnect()
    fun disconnect()

    suspend fun startRecording(): Boolean
    suspend fun stopRecording(): Boolean
    suspend fun captureSnapshot(): Boolean
    suspend fun lockEvent(): Boolean

    suspend fun setChannel(channel: CameraChannel)
    suspend fun pausePreview()
    suspend fun resumePreview()
    suspend fun refreshTelemetry()

    companion object {
        const val DEFAULT_SSID = "INNOVV_K7_5.8G"
    }
}

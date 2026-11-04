package com.zoloft.innovv.app.platform

import kotlinx.coroutines.flow.StateFlow

interface CameraNetworkBinder {
    val state: StateFlow<CameraNetworkState>

    suspend fun joinAndBind(ssid: String, passphrase: String? = null): NetworkBindingResult
    suspend fun bind(): NetworkBindingResult
    fun unbind()
    suspend fun currentSsid(): String?
    fun openSystemWifiSettings()
}

sealed interface CameraNetworkState {
    data object Unbound : CameraNetworkState
    data object Binding : CameraNetworkState
    data class Bound(val ssid: String, val networkHandle: String? = null) : CameraNetworkState
    data class Lost(val reason: String) : CameraNetworkState
    data class Failed(val reason: NetworkFailure) : CameraNetworkState
}

sealed interface NetworkBindingResult {
    data class Success(val ssid: String) : NetworkBindingResult
    data class Failure(val reason: NetworkFailure, val detail: String? = null) : NetworkBindingResult
    data object Cancelled : NetworkBindingResult
}

enum class NetworkFailure {
    PERMISSION_DENIED,
    SSID_NOT_FOUND,
    WRONG_PASSPHRASE,
    USER_DENIED,
    TIMEOUT,
    BIND_FAILED,
    UNKNOWN,
}

expect fun createCameraNetworkBinder(): CameraNetworkBinder

package com.zoloft.innovv.app.platform

import com.zoloft.innovv.app.domain.model.WifiNetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.NetworkExtension.NEHotspotConfiguration
import platform.NetworkExtension.NEHotspotConfigurationManager
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.Foundation.NSURL
import kotlin.coroutines.resume

class IosCameraNetworkBinder : CameraNetworkBinder {
    private val _state = MutableStateFlow<CameraNetworkState>(CameraNetworkState.Unbound)
    override val state: StateFlow<CameraNetworkState> = _state.asStateFlow()

    override suspend fun joinAndBind(ssid: String, passphrase: String?): NetworkBindingResult {
        _state.value = CameraNetworkState.Binding
        if (passphrase.isNullOrBlank()) {
            // Without a passphrase we cannot create a WPA hotspot configuration.
            // The user joins the camera AP in system Wi-Fi settings; the SDK
            // then reaches the camera over the local route.
            _state.value = CameraNetworkState.Bound(ssid)
            return NetworkBindingResult.Success(ssid)
        }
        return try {
            val configuration = NEHotspotConfiguration(sSID = ssid, passphrase = passphrase, isWEP = false)
            withTimeout(25_000) {
                suspendCancellableCoroutine { continuation ->
                    NEHotspotConfigurationManager.sharedManager.applyConfiguration(configuration) { error ->
                        if (error == null) {
                            _state.value = CameraNetworkState.Bound(ssid)
                            if (continuation.isActive) {
                                continuation.resume(NetworkBindingResult.Success(ssid))
                            }
                        } else {
                            _state.value = CameraNetworkState.Failed(NetworkFailure.WRONG_PASSPHRASE)
                            if (continuation.isActive) {
                                continuation.resume(
                                    NetworkBindingResult.Failure(
                                        NetworkFailure.WRONG_PASSPHRASE,
                                        error.localizedDescription,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            _state.value = CameraNetworkState.Failed(NetworkFailure.UNKNOWN)
            NetworkBindingResult.Failure(NetworkFailure.UNKNOWN, failure.message)
        }
    }

    override suspend fun scanNetworks(): List<WifiNetworkInfo> {
        // iOS does not expose arbitrary Wi-Fi scan results to third-party apps.
        // The user is directed to system Wi-Fi settings for manual selection.
        return emptyList()
    }

    override suspend fun bind(): NetworkBindingResult {
        // iOS routes RFC1918 camera traffic over the active Wi-Fi interface when
        // the camera AP is the only route. The SDK's URLSession and VLCKit both
        // use that route; explicit per-socket binding is not exposed by iOS.
        _state.value = CameraNetworkState.Bound("camera")
        return NetworkBindingResult.Success("camera")
    }

    override fun unbind() {
        _state.value = CameraNetworkState.Unbound
    }

    override suspend fun currentSsid(): String? = null

    override suspend fun currentGateway(): String? = null

    override fun openSystemWifiSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}

actual fun createCameraNetworkBinder(): CameraNetworkBinder = IosCameraNetworkBinder()

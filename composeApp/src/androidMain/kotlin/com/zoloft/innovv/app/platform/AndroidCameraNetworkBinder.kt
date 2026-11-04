package com.zoloft.innovv.app.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.annotation.RequiresApi
import com.zoloft.innovv.app.InnovvApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class AndroidCameraNetworkBinder : CameraNetworkBinder {
    private val context: Context = InnovvApplication.instance.applicationContext
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow<CameraNetworkState>(CameraNetworkState.Unbound)
    override val state: StateFlow<CameraNetworkState> = _state.asStateFlow()

    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    override suspend fun joinAndBind(ssid: String, passphrase: String?): NetworkBindingResult {
        _state.value = CameraNetworkState.Binding
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                joinWithSpecifier(ssid, passphrase)
            } else {
                bindActiveWifi(ssid)
            }
        } catch (failure: SecurityException) {
            _state.value = CameraNetworkState.Failed(NetworkFailure.PERMISSION_DENIED)
            NetworkBindingResult.Failure(NetworkFailure.PERMISSION_DENIED, failure.message)
        } catch (failure: Throwable) {
            _state.value = CameraNetworkState.Failed(NetworkFailure.UNKNOWN)
            NetworkBindingResult.Failure(NetworkFailure.UNKNOWN, failure.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun joinWithSpecifier(ssid: String, passphrase: String?): NetworkBindingResult {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .apply {
                if (!passphrase.isNullOrBlank()) {
                    setWpa2Passphrase(passphrase)
                }
            }
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        return withTimeout(20_000) {
            suspendCancellableCoroutine { continuation ->
                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        boundNetwork = network
                        _state.value = CameraNetworkState.Bound(ssid)
                        if (continuation.isActive) {
                            continuation.resume(NetworkBindingResult.Success(ssid))
                        }
                    }

                    override fun onUnavailable() {
                        _state.value = CameraNetworkState.Failed(NetworkFailure.SSID_NOT_FOUND)
                        if (continuation.isActive) {
                            continuation.resume(
                                NetworkBindingResult.Failure(
                                    NetworkFailure.SSID_NOT_FOUND,
                                    "The camera access point was not found.",
                                ),
                            )
                        }
                    }

                    override fun onLost(network: Network) {
                        if (boundNetwork == network) {
                            boundNetwork = null
                            _state.value = CameraNetworkState.Lost("Camera Wi-Fi network was lost")
                        }
                    }
                }
                callback = networkCallback
                connectivityManager.requestNetwork(request, networkCallback)
                continuation.invokeOnCancellation {
                    runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
                }
            }
        }
    }

    private suspend fun bindActiveWifi(ssid: String): NetworkBindingResult {
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
        if (network != null && capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            connectivityManager.bindProcessToNetwork(network)
            boundNetwork = network
            _state.value = CameraNetworkState.Bound(ssid)
            return NetworkBindingResult.Success(ssid)
        }
        _state.value = CameraNetworkState.Failed(NetworkFailure.BIND_FAILED)
        return NetworkBindingResult.Failure(
            NetworkFailure.BIND_FAILED,
            "Join the camera Wi-Fi network in system settings first.",
        )
    }

    override suspend fun bind(): NetworkBindingResult {
        val ssid = currentSsid() ?: "camera"
        return bindActiveWifi(ssid)
    }

    override fun unbind() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
        boundNetwork = null
        connectivityManager.bindProcessToNetwork(null)
        _state.value = CameraNetworkState.Unbound
    }

    @Suppress("DEPRECATION")
    override suspend fun currentSsid(): String? {
        return runCatching {
            wifiManager.connectionInfo?.ssid?.trim('"')
        }.getOrNull()
    }

    override fun openSystemWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

actual fun createCameraNetworkBinder(): CameraNetworkBinder = AndroidCameraNetworkBinder()

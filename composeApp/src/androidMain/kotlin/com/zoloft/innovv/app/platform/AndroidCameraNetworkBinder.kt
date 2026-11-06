package com.zoloft.innovv.app.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.provider.Settings
import android.content.Intent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.zoloft.innovv.app.domain.model.WifiNetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

class AndroidCameraNetworkBinder : CameraNetworkBinder {
    private val context: Context = AndroidPlatformContext.application.applicationContext
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _state = MutableStateFlow<CameraNetworkState>(CameraNetworkState.Unbound)
    override val state: StateFlow<CameraNetworkState> = _state.asStateFlow()

    private var boundNetwork: Network? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
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

        return withTimeout(20_000.milliseconds) {
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

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private suspend fun bindActiveWifi(ssid: String): NetworkBindingResult {
        // Android often keeps cellular as the "active" network when the camera
        // AP has no internet. Search all Wi-Fi networks, not just the active one.
        val network = findWifiNetwork()
        if (network != null) {
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

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private fun findWifiNetwork(): Network? {
        val active = connectivityManager.activeNetwork
        val activeCapabilities = active?.let { connectivityManager.getNetworkCapabilities(it) }
        if (active != null && activeCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            return active
        }
        return connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override suspend fun bind(): NetworkBindingResult {
        val ssid = currentSsid() ?: "camera"
        return bindActiveWifi(ssid)
    }

    @SuppressLint("MissingPermission")
    override suspend fun scanNetworks(): List<WifiNetworkInfo> = withContext(Dispatchers.IO) {
        val current = currentSsid()
        runCatching { wifiManager.startScan() }
        delay(700.milliseconds)
        @Suppress("DEPRECATION")
        val results = runCatching { wifiManager.scanResults }.getOrElse { emptyList() }
        results.asSequence()
            .filter { it.SSID.isNotBlank() && it.SSID != "<unknown ssid>" }
            .distinctBy { it.SSID }
            .sortedByDescending { it.level }
            .map { result ->
                val capabilities = result.capabilities.uppercase()
                WifiNetworkInfo(
                    ssid = result.SSID,
                    rssiDbm = result.level,
                    secured = capabilities.contains("WPA") ||
                        capabilities.contains("WEP") ||
                        capabilities.contains("EAP") ||
                        capabilities.contains("SAE"),
                    isCurrent = result.SSID == current,
                )
            }
            .toList()
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    override suspend fun currentGateway(): String? {
        val network = findWifiNetwork() ?: return null
        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress
    }

    override fun unbind() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
        boundNetwork = null
        connectivityManager.bindProcessToNetwork(null)
        _state.value = CameraNetworkState.Unbound
    }

    @SuppressLint("MissingPermission")
    override suspend fun currentSsid(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            currentSsidFromActiveNetwork()
        } else {
            null
        } ?: runCatching {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo?.ssid?.trim('"')
        }.getOrNull()?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun currentSsidFromActiveNetwork(): String? {
        val network = findWifiNetwork() ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null
        val wifiInfo = capabilities.transportInfo as? WifiInfo ?: return null
        return wifiInfo.ssid?.trim('"')?.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    override fun openSystemWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

actual fun createCameraNetworkBinder(): CameraNetworkBinder = AndroidCameraNetworkBinder()

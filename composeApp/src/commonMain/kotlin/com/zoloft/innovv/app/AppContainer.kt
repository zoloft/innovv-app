package com.zoloft.innovv.app

import com.zoloft.innovv.app.data.demo.DemoCameraController
import com.zoloft.innovv.app.data.demo.DemoGalleryRepository
import com.zoloft.innovv.app.data.debug.DebugLogManager
import com.zoloft.innovv.app.data.demo.DemoSettingsRepository
import com.zoloft.innovv.app.data.settings.SettingsCache
import com.zoloft.innovv.app.data.sdk.SdkCameraSession
import com.zoloft.innovv.app.data.sdk.SdkGalleryRepository
import com.zoloft.innovv.app.data.sdk.SdkSettingsRepository
import com.zoloft.innovv.app.domain.CameraController
import com.zoloft.innovv.app.domain.GalleryRepository
import com.zoloft.innovv.app.domain.SettingsRepository
import com.zoloft.innovv.app.platform.createHaptics
import com.zoloft.innovv.app.platform.isDebugBuild
import com.zoloft.innovv.app.presentation.dashboard.DashboardViewModel
import com.zoloft.innovv.app.presentation.gallery.GalleryViewModel
import com.zoloft.innovv.app.presentation.settings.SettingsViewModel

class AppContainer(
    useDemo: Boolean = false,
) {
    val debugLog = DebugLogManager(isDebugBuild = isDebugBuild)
    private val sdkSession = if (useDemo) null else SdkCameraSession(debugLog = debugLog)

    val camera: CameraController = sdkSession ?: DemoCameraController()
    val galleryRepository: GalleryRepository = sdkSession?.let { SdkGalleryRepository(it) }
        ?: DemoGalleryRepository()
    val settingsRepository: SettingsRepository = sdkSession?.let { SdkSettingsRepository(it) }
        ?: DemoSettingsRepository()

    val dashboard = DashboardViewModel(camera, createHaptics())
    val gallery = GalleryViewModel(camera, galleryRepository)
    val settings = SettingsViewModel(camera, settingsRepository, SettingsCache(), debugLog)
}

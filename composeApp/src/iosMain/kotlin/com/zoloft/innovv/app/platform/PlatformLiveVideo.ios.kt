package com.zoloft.innovv.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun PlatformLiveVideo(url: String, modifier: Modifier) {
    // VLCKit/MobileVLCKit is the intended iOS RTSP implementation. The
    // Compose live-view surface remains visible until the player is linked.
}

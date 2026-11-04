package com.zoloft.innovv.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform live-video surface. Android uses Media3 ExoPlayer with RTSP/TCP.
 * iOS keeps this as an integration point for VLCKit; the surrounding Compose
 * surface shows the live-view placeholder until the player is linked.
 */
@Composable
expect fun PlatformLiveVideo(
    url: String,
    modifier: Modifier = Modifier,
    onFrame: () -> Unit = {},
    onError: (String) -> Unit = {},
)

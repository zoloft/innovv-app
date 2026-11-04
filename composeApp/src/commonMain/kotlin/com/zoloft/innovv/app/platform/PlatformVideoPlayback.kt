package com.zoloft.innovv.app.platform

/**
 * Whether the current platform has a working video surface for RTSP/HTTP
 * playback. Android uses Media3; iOS is waiting for the VLCKit integration.
 */
expect val isPlatformVideoPlaybackAvailable: Boolean

package com.zoloft.innovv.app.platform

import android.content.pm.ApplicationInfo

actual val isDebugBuild: Boolean
    get() = (AndroidPlatformContext.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

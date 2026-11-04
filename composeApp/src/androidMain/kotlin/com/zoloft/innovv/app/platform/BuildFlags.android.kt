package com.zoloft.innovv.app.platform

import android.content.pm.ApplicationInfo
import com.zoloft.innovv.app.InnovvApplication

actual val isDebugBuild: Boolean
    get() = (InnovvApplication.instance.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

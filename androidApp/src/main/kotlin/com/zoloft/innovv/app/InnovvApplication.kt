package com.zoloft.innovv.app

import android.app.Application
import com.zoloft.innovv.app.platform.AndroidPlatformContext

class InnovvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidPlatformContext.initialize(this)
    }
}

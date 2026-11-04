package com.zoloft.innovv.app.platform

import android.app.Application

/**
 * Application context bridge owned by the shared KMP library. The Android app
 * module initializes it from its Application class before any shared code runs.
 */
object AndroidPlatformContext {
    lateinit var application: Application
        private set

    fun initialize(application: Application) {
        this.application = application
    }
}

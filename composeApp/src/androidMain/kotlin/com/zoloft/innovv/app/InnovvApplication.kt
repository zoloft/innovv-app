package com.zoloft.innovv.app

import android.app.Application

class InnovvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: InnovvApplication
            private set
    }
}

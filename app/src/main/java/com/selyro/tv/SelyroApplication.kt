package com.selyro.tv

import android.app.Application
import android.util.Log

class SelyroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        CrashReporter.markStage(this, "application:onCreate:ready")
        Log.i("SelyroStartup", "application:ready")
    }
}

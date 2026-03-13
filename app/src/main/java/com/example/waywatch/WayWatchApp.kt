package com.example.waywatch

import android.app.Application
import com.example.waywatch.di.appModule
import com.example.waywatch.util.StrictModeConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

class WayWatchApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppGlobals.init(this)

        if (BuildConfig.STRICT_MODE_ENABLED) {
            StrictModeConfig.enableForDebug()
        }

        try {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val instance = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("setCustomKey", String::class.java, String::class.java)
                .invoke(instance, "buildType", BuildConfig.BUILD_TYPE)
            clazz.getMethod("setCustomKey", String::class.java, String::class.java)
                .invoke(instance, "versionName", BuildConfig.VERSION_NAME)
        } catch (_: Throwable) {}

        try {
            val logger = analytics.FirebaseAnalyticsLogger()
            logger.logEvent("app_start", mapOf("buildType" to BuildConfig.BUILD_TYPE))
        } catch (_: Throwable) {}

        startKoin {
            androidLogger()
            androidContext(this@WayWatchApp)
            workManagerFactory()
            modules(appModule)
        }
    }
}

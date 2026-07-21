package com.dokeraj.androtainer.globalvars

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dokeraj.androtainer.models.Credential
import com.dokeraj.androtainer.models.LogSettings
import com.dokeraj.androtainer.models.retrofit.AppSettings
import com.dokeraj.androtainer.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GlobalApp : Application(), Configuration.Provider {
    var credentials: MutableMap<String, Credential> = mutableMapOf()
    var currentUser: Credential? = null
    var logSettings: LogSettings? = null
    var appSettings: AppSettings? = null

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            NotificationHelper.CHANNEL_ID,
            "Container threshold alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a container exceeds its CPU/memory threshold"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

package com.boss.ipv6ddns

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import com.boss.ipv6ddns.di.AppContainer

class Ipv6DdnsApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        createNotificationChannel()
        // WorkManager is initialized via the androidx.startup Initializer that we
        // *do* allow to run (we only removed the WorkManagerInitializer in the manifest
        // to avoid the double-init path). Configuration.Provider is the recommended
        // hook for on-demand init in the Application class.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIF_CHANNEL_ID = "ipv6ddns_running"
        const val NOTIF_ID = 1001

        @Volatile
        private var instance: Ipv6DdnsApp? = null

        fun container(): AppContainer = requireNotNull(instance).container
    }
}

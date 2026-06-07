package com.boss.ipv6ddns.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.boss.ipv6ddns.Ipv6DdnsApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val app = context.applicationContext
                val container = Ipv6DdnsApp.container()
                if (container.configRepository.hasFullConfig()) {
                    val i = Intent(app, DDNSForegroundService::class.java)
                        .setAction(DDNSForegroundService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(i)
                    } else {
                        app.startService(i)
                    }
                }
            }
        }
    }
}

package com.boss.ipv6ddns.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.boss.ipv6ddns.Ipv6DdnsApp
import com.boss.ipv6ddns.MainActivity
import com.boss.ipv6ddns.R
import com.boss.ipv6ddns.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DDNSForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var uiStateObserverJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Ipv6DdnsApp.container().logRepository.i(TAG, getString(R.string.msg_service_started))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        // Use the shared scheduler singleton so the foreground service and the
        // WorkManager fallback (and the "check now" notification action) all
        // share the same in-flight tracking.
        val container = Ipv6DdnsApp.container()
        val scheduler = container.scheduler
        when (action) {
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
            ACTION_CHECK_NOW -> {
                // runOnce is suspend — launch it so we don't block the service thread.
                scope.launch { scheduler.runOnce() }
            }
            ACTION_START -> {
                startInForeground()
                val cfg = container.configRepository.loadConfig()
                scheduler.startLoop(cfg.intervalMinutes)
                container.logRepository.i(
                    TAG, getString(R.string.msg_work_scheduled)
                )
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Ipv6DdnsApp.NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Ipv6DdnsApp.NOTIF_ID, notification)
        }
        observeState()
    }

    private fun observeState() {
        uiStateObserverJob?.cancel()
        val container = Ipv6DdnsApp.container()
        uiStateObserverJob = scope.launch {
            container.ddnsState.state.collectLatest {
                val nm = androidx.core.app.NotificationManagerCompat.from(this@DDNSForegroundService)
                if (nm.areNotificationsEnabled()) {
                    val notif = buildNotification()
                    nm.notify(Ipv6DdnsApp.NOTIF_ID, notif)
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        val container: AppContainer = Ipv6DdnsApp.container()
        val state = container.ddnsState.state.value
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DDNSForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val checkIntent = PendingIntent.getService(
            this, 2,
            Intent(this, DDNSForegroundService::class.java).setAction(ACTION_CHECK_NOW),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = getString(R.string.notif_title)
        val text = buildString {
            if (state.lastKnownIpv6.isNotBlank()) {
                append("IPv6: ").append(state.lastKnownIpv6)
            } else {
                append(getString(R.string.notif_text_idle))
            }
            if (state.lastError.isNotBlank()) {
                append("  ·  ⚠ ").append(state.lastError.take(80))
            }
            if (state.lastCheckAtMillis > 0) {
                append("  ·  ")
                append(getString(R.string.status_last_check)).append(": ")
                append(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.lastCheckAtMillis)))
            }
        }

        return NotificationCompat.Builder(this, Ipv6DdnsApp.NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(0, getString(R.string.status_check_now), checkIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stop() {
        Ipv6DdnsApp.container().scheduler.stopLoop()
        Ipv6DdnsApp.container().logRepository.i(TAG, getString(R.string.msg_service_stopped))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Ipv6DdnsApp.container().scheduler.stopLoop()
        uiStateObserverJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "DDNSService"

        const val ACTION_START = "com.boss.ipv6ddns.action.START"
        const val ACTION_STOP = "com.boss.ipv6ddns.action.STOP"
        const val ACTION_CHECK_NOW = "com.boss.ipv6ddns.action.CHECK_NOW"

        fun start(ctx: Context) {
            val i = Intent(ctx, DDNSForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            val i = Intent(ctx, DDNSForegroundService::class.java).setAction(ACTION_STOP)
            ctx.startService(i)
        }
    }
}

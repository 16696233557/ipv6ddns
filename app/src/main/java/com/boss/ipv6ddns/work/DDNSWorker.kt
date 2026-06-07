package com.boss.ipv6ddns.work

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.boss.ipv6ddns.Ipv6DdnsApp
import com.boss.ipv6ddns.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class DDNSWorker(
    appCtx: Context,
    params: WorkerParameters
) : CoroutineWorker(appCtx, params) {

    /**
     * WM hard cap is 10 minutes per worker run (and even less under Doze + bg
     * restrictions). We pin a hard ceiling at 8 minutes so we always return
     * cleanly well before the OS kills us. Normal probe+update finishes in
     * 5-15 s, so this only fires on a wedged network or a stuck aliyun RPC.
     */
    private val runTimeoutMs: Long = 8 * 60 * 1000L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val container = Ipv6DdnsApp.container()
        container.logRepository.i(TAG, ctx.getString(R.string.msg_work_run))

        // Promote this short-lived worker to foreground so WM 2.9+ does not
        // silently drop it under expedited work pressure. The notification is
        // the same one used by the long-lived foreground service — different ID
        // (NOTIF_ID + 1) to avoid clobbering its ongoing notification.
        runCatching { setForeground(getForegroundInfo()) }
            .onFailure { container.logRepository.w(TAG, "setForeground failed: ${it.message}") }

        // If the foreground service is already running, skip
        if (container.ddnsState.state.value.serviceRunning) {
            container.logRepository.i(TAG, ctx.getString(R.string.msg_work_duplicate))
            return@withContext Result.success()
        }

        // If we have no config, nothing to do
        if (!container.configRepository.hasFullConfig()) {
            container.logRepository.w(TAG, "no config, skip")
            return@withContext Result.success()
        }

        // Run a single iteration and wait for it. The scheduler is the shared
        // instance from the container (not a fresh one) so its `running` flag
        // and rate-limit semantics stay consistent with the foreground service
        // and the "check now" UI path. Bounded by [runTimeoutMs] so a stuck
        // aliyun RPC cannot push us past WM's 10-minute hard cap.
        val outcome = withTimeoutOrNull(runTimeoutMs) {
            container.scheduler.runOnce()
            true
        }
        if (outcome == null) {
            container.logRepository.w(TAG, "runOnce timed out after ${runTimeoutMs / 1000}s, returning retry")
            return@withContext Result.retry()
        }
        Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val ctx = applicationContext
        val notif = NotificationCompat.Builder(ctx, Ipv6DdnsApp.NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(ctx.getString(R.string.notif_text_idle))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(Ipv6DdnsApp.NOTIF_ID + 1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(Ipv6DdnsApp.NOTIF_ID + 1, notif)
        }
    }

    companion object {
        private const val TAG = "DDNSWorker"
        const val WORK_NAME = "ipv6_ddns_periodic"
    }
}

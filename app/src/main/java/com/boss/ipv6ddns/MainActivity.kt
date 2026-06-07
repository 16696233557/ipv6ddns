package com.boss.ipv6ddns

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.boss.ipv6ddns.ui.MainScreen
import com.boss.ipv6ddns.ui.theme.IPv6DDNSTheme
import com.boss.ipv6ddns.work.DDNSWorker
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Mark as "asked" regardless of result so we don't pester the user
        // every cold start. UI surfaces a "open settings" hint if denied.
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIF_PERM_ASKED, true)
            .putBoolean(KEY_NOTIF_PERM_GRANTED, granted)
            .apply()
    }

    /**
     * In-app re-request entry point. Safe to call from any Composable that has
     * access to the host Activity. The system "shouldShowRequestPermissionRationale"
     * gate decides whether the OS dialog actually appears — if the user has ticked
     * "Don't ask again", the call is a no-op and the UI must fall back to the
     * app-notification-settings intent.
     */
    fun requestNotificationPermissionInApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermission()
        ensurePeriodicWorker()
        // NOTE: do NOT auto-launch the battery whitelist dialog here.
        // Google Play policy forbids it, and UX-wise the user hasn't seen the
        // app yet. StatusScreen surfaces a button that triggers it.

        setContent {
            IPv6DDNSTheme {
                MainScreen(activity = this)
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            // System says granted (e.g. user granted on a prior install
            // before the prefs flag was set). Mark asked so we don't re-prompt.
            prefs.edit()
                .putBoolean(KEY_NOTIF_PERM_ASKED, true)
                .putBoolean(KEY_NOTIF_PERM_GRANTED, true)
                .apply()
            return
        }
        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun ensurePeriodicWorker() {
        // 15 minutes is the WM hard floor for PeriodicWorkRequest. The actual
        // interval the user picks (1/5/10/30/60) is honoured by the foreground
        // service loop — this periodic worker is only the safety net for when
        // the OS kills the service. Marking it expedited (with
        // RUN_AS_NON_EXPEDITED fallback) gives it the highest priority bucket
        // the system can offer, which materially reduces the chance WM will
        // starve the worker into oblivion on aggressive OEMs.
        val req = PeriodicWorkRequestBuilder<DDNSWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DDNSWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            req
        )
    }

    companion object {
        private const val PREFS = "ipv6ddns_ui_prefs"
        private const val KEY_NOTIF_PERM_ASKED = "notif_perm_asked"
        private const val KEY_NOTIF_PERM_GRANTED = "notif_perm_granted"
    }
}

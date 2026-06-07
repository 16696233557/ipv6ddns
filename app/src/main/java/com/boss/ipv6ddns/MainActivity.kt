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
        // On first launch we just mark "asked=true" so the StatusScreen hint
        // card has correct state from the get-go. We do NOT auto-launch the
        // system permission dialog because:
        //   1. The dialog is shown via MIUI's GrantPermissionsActivity shell,
        //      which is an opaque Activity that on MIUI 14 cannot be
        //      interacted with via adb/input commands on a brand-new install
        //      (system_server denies INJECT_EVENTS to the shell uid 2000).
        //   2. Auto-prompting before the user has even seen the app is
        //      hostile UX and Google Play frowns on it.
        // The "Ask again" / "Open settings" pair on the StatusScreen gives
        // the user a single-tap way to grant the permission when ready.
        if (!prefs.getBoolean(KEY_NOTIF_PERM_ASKED, false)) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            prefs.edit()
                .putBoolean(KEY_NOTIF_PERM_ASKED, true)
                .putBoolean(KEY_NOTIF_PERM_GRANTED, granted)
                .apply()
        }
    }

    private fun ensurePeriodicWorker() {
        // 15 minutes is the WM hard floor for PeriodicWorkRequest. The actual
        // interval the user picks (1/5/10/30/60) is honoured by the foreground
        // service loop — this periodic worker is only the safety net for when
        // the OS kills the service.
        //
        // NOTE: PeriodicWorkRequest cannot be marked expedited in WM 2.9
        // (calling .setExpedited() throws IllegalArgumentException at
        // .build()). Expediting is only available on OneTimeWorkRequest, so
        // the foreground promotion inside DDNSWorker.getForegroundInfo() +
        // setForeground() is what keeps this periodic job from being starved
        // on aggressive OEMs. The WM hard cap of 10 min is enforced inside
        // the worker via withTimeoutOrNull.
        val req = PeriodicWorkRequestBuilder<DDNSWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(5, TimeUnit.MINUTES)
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

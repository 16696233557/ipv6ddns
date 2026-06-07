package com.boss.ipv6ddns.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.boss.ipv6ddns.Ipv6DdnsApp
import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.LogEntry
import com.boss.ipv6ddns.data.LogRepository
import com.boss.ipv6ddns.data.ProbeSource
import com.boss.ipv6ddns.di.AppContainer
import com.boss.ipv6ddns.network.Ipv6Probe
import com.boss.ipv6ddns.service.DDNSForegroundService
import com.boss.ipv6ddns.service.DDNSState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the three screens. Owns no service state — it just exposes the
 * AppContainer's flows and forwards user intents.
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<DDNSState.Snapshot> = container.ddnsState.state
    val logs: StateFlow<List<LogEntry>> = container.logRepository.entries

    val configFlow: StateFlow<DDNSConfig> = container.configRepository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = container.configRepository.loadConfig()
        )

    fun loadConfig(): DDNSConfig = container.configRepository.loadConfig()

    fun saveConfig(cfg: DDNSConfig, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            container.configRepository.saveConfig(cfg)
            onDone(true)
        }
    }

    fun clearLogs() = container.logRepository.clear()

    /**
     * Fire-and-forget single-source probe test from the UI. Result is delivered
     * via [onResult] on the main thread. The network call runs on the probe's
     * own IO dispatcher so the Composable doesn't need its own scope.
     */
    fun testProbeSource(source: ProbeSource, onResult: (Ipv6Probe.Result) -> Unit) {
        viewModelScope.launch {
            val res = container.ipv6Probe.testOne(source)
            onResult(res)
        }
    }

    fun startService(context: Context) {
        DDNSForegroundService.start(context)
    }

    fun stopService(context: Context) {
        DDNSForegroundService.stop(context)
    }

    fun checkNow(context: Context) {
        // If the service is not running, the action still enqueues an update.
        val i = Intent(context, DDNSForegroundService::class.java)
            .setAction(DDNSForegroundService.ACTION_CHECK_NOW)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }.onFailure {
            // Service not running and we cannot start a foreground one from a UI tap
            // when the app is in the background. Start the service in START mode.
            DDNSForegroundService.start(context)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openBatteryWhitelist(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /**
     * True when the user has been prompted for POST_NOTIFICATIONS at least once
     * and either denied it or is on a pre-Tiramisu device that has it implicitly.
     * Used to decide whether to show the "open settings" hint card on Status.
     */
    fun isNotificationDenied(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val prefs = context.getSharedPreferences(
            "ipv6ddns_ui_prefs", Context.MODE_PRIVATE
        )
        if (!prefs.getBoolean("notif_perm_asked", false)) return false
        return !prefs.getBoolean("notif_perm_granted", false)
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }
            }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
                "Unknown ViewModel ${modelClass.name}"
            }
            return MainViewModel(container) as T
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = Factory(Ipv6DdnsApp.container())
    }
}

package com.boss.ipv6ddns.service

import com.boss.ipv6ddns.Ipv6DdnsApp
import com.boss.ipv6ddns.R
import com.boss.ipv6ddns.data.DDNSConfig
import com.boss.ipv6ddns.data.LogRepository
import com.boss.ipv6ddns.network.AliyunDnsClient
import com.boss.ipv6ddns.network.Ipv6Probe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The actual DDNS worker. Used by both the ForegroundService and the WorkManager worker.
 *
 * One instance per run, with a single in-flight job; calling run() while a run is
 * active is a no-op (caller logs and moves on).
 */
class DDNSScheduler(
    private val probe: Ipv6Probe,
    private val dns: AliyunDnsClient,
    private val configRepo: com.boss.ipv6ddns.data.ConfigRepository,
    private val log: LogRepository,
    private val state: DDNSState
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var running: Boolean = false
    @Volatile private var loopJob: Job? = null

    /**
     * Execute one probe→update cycle. Suspends until the cycle finishes so callers
     * (the periodic loop, the WorkManager worker, the "check now" action) all get
     * the actual completion signal instead of fire-and-forget.
     *
     * If a run is already in flight, returns immediately without blocking — the
     * in-flight run will deliver the result.
     */
    suspend fun runOnce() {
        if (running) {
            log.i(TAG, "Scheduler runOnce skipped: another run in progress")
            return
        }
        running = true
        try {
            doRun()
        } catch (t: Throwable) {
            log.e(TAG, "runOnce crashed: ${t.message}")
        } finally {
            running = false
        }
    }

    /**
     * Start the periodic loop. If already running, no-op. Each iteration awaits
     * the full probe+update cycle before sleeping the configured interval, so
     * the effective period is `max(interval, actual cycle duration)` and the
     * "30 minutes" the user picks is honoured as a floor, not a drift-prone
     * launch-then-sleep approximation.
     *
     * Re-reads `intervalMinutes` from the repo at the top of every cycle, so
     * a config change in the ConfigScreen is picked up at the next iteration
     * boundary (≤ one interval) without needing a service restart.
     *
     * Sleeps in one shot whenever the remaining interval is > 5 s, then
     * short-polls the last 5 s to keep `nextCheckAt` in `DDNSState` accurate
     * for the StatusScreen countdown (which can only update by re-reading
     * state — we don't have a 1-Hz callback chain).
     */
    fun startLoop(@Suppress("UNUSED_PARAMETER") intervalMinutes: Int) {
        stopLoop()
        log.i(TAG, "Starting loop")
        state.setRunning(true)
        loopJob = scope.launch {
            try {
                while (true) {
                    val cfg = configRepo.loadConfig()
                    val intervalMs = (cfg.intervalMinutes.coerceAtLeast(1) * 60L) * 1000L
                    log.i(TAG, "Probe cycle, interval=${cfg.intervalMinutes}min")
                    runOnce()
                    val end = System.currentTimeMillis() + intervalMs
                    state.setNextCheckAt(end)
                    val longSleepMs = (intervalMs - 5_000L).coerceAtLeast(0L)
                    if (longSleepMs > 0L) {
                        delay(longSleepMs)
                    }
                    while (System.currentTimeMillis() < end) {
                        delay(500L)
                    }
                }
            } catch (t: Throwable) {
                log.e(TAG, "loop crashed: ${t.message}")
            } finally {
                state.setRunning(false)
                state.setNextCheckAt(0L)
            }
        }
    }

    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        state.setRunning(false)
        state.setNextCheckAt(0L)
    }

    fun isLooping(): Boolean = loopJob?.isActive == true

    private suspend fun doRun() {
        state.setLastCheck()
        log.i(TAG, "Probe start")
        val config = configRepo.loadConfig()
        state.setConfigSnapshot(config)

        if (!configRepo.hasFullConfig()) {
            log.w(TAG, "Config incomplete, skip")
            state.setLastError("Config incomplete")
            return
        }

        val probeResult = withContext(Dispatchers.IO) { probe.probe(config.probeSources) }
        when (probeResult) {
            is Ipv6Probe.Result.Fail -> {
                state.setLastError(probeResult.reason)
                configRepo.saveConfig(config.copy(lastError = probeResult.reason, lastCheckAtMillis = System.currentTimeMillis()))
                return
            }
            is Ipv6Probe.Result.Ok -> {
                val ipv6 = probeResult.ipv6
                if (config.lastKnownIpv6.equals(ipv6, ignoreCase = true)
                    && config.lastDnsValue.equals(ipv6, ignoreCase = true)) {
                    log.i(TAG, "IPv6 unchanged, skip")
                    configRepo.saveConfig(config.copy(
                        lastError = "",
                        lastCheckAtMillis = System.currentTimeMillis()
                    ))
                    return
                }
                val syncResult = dns.sync(config, ipv6)
                when (syncResult) {
                    is AliyunDnsClient.Result.Created -> {
                        state.setLastSuccess(ipv6, syncResult.value, syncResult.recordId)
                        state.setLastError("")
                        configRepo.saveConfig(config.copy(
                            lastKnownIpv6 = ipv6,
                            lastKnownIpv6Hash = ipv6.hashCode().toLong(),
                            lastDnsValue = syncResult.value,
                            lastDnsRecordId = syncResult.recordId,
                            lastError = "",
                            lastCheckAtMillis = System.currentTimeMillis(),
                            lastSuccessAtMillis = System.currentTimeMillis()
                        ))
                    }
                    is AliyunDnsClient.Result.Updated -> {
                        state.setLastSuccess(ipv6, syncResult.value, syncResult.recordId)
                        state.setLastError("")
                        configRepo.saveConfig(config.copy(
                            lastKnownIpv6 = ipv6,
                            lastKnownIpv6Hash = ipv6.hashCode().toLong(),
                            lastDnsValue = syncResult.value,
                            lastDnsRecordId = syncResult.recordId,
                            lastError = "",
                            lastCheckAtMillis = System.currentTimeMillis(),
                            lastSuccessAtMillis = System.currentTimeMillis()
                        ))
                    }
                    is AliyunDnsClient.Result.Unchanged -> {
                        state.setLastSuccess(ipv6, syncResult.value, syncResult.recordId)
                        state.setLastError("")
                        configRepo.saveConfig(config.copy(
                            lastKnownIpv6 = ipv6,
                            lastDnsValue = syncResult.value,
                            lastDnsRecordId = syncResult.recordId,
                            lastError = "",
                            lastCheckAtMillis = System.currentTimeMillis()
                        ))
                    }
                    is AliyunDnsClient.Result.Failure -> {
                        state.setLastError(syncResult.reason)
                        configRepo.saveConfig(config.copy(
                            lastError = syncResult.reason,
                            lastCheckAtMillis = System.currentTimeMillis()
                        ))
                    }
                }
            }
        }
    }

    fun shutdown() {
        stopLoop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "DDNSScheduler"
    }
}

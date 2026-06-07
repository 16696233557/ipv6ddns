package com.boss.ipv6ddns.di

import android.content.Context
import com.boss.ipv6ddns.data.ConfigRepository
import com.boss.ipv6ddns.data.LogRepository
import com.boss.ipv6ddns.network.AliyunDnsClient
import com.boss.ipv6ddns.network.Ipv6Probe
import com.boss.ipv6ddns.network.OkHttpProvider
import com.boss.ipv6ddns.service.DDNSScheduler
import com.boss.ipv6ddns.service.DDNSState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled DI container. Single instance lives on the Application.
 */
class AppContainer(private val appContext: Context) {

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpProvider.create()
    }

    val configRepository: ConfigRepository by lazy {
        ConfigRepository(appContext)
    }

    val logRepository: LogRepository by lazy {
        LogRepository()
    }

    val ipv6Probe: Ipv6Probe by lazy {
        Ipv6Probe(okHttpClient, moshi, logRepository)
    }

    val aliyunDnsClient: AliyunDnsClient by lazy {
        AliyunDnsClient(logRepository)
    }

    val ddnsState: DDNSState by lazy {
        DDNSState()
    }

    /**
     * Single shared scheduler. Both the foreground service and the WorkManager
     * worker (and the "check now" button) call into this one instance so its
     * in-flight tracking and rate-limit semantics are consistent across paths.
     * The container never calls `shutdown()` on it — the process death handles
     * cleanup, and on next start the singleton is recreated.
     */
    val scheduler: DDNSScheduler by lazy {
        DDNSScheduler(
            probe = ipv6Probe,
            dns = aliyunDnsClient,
            configRepo = configRepository,
            log = logRepository,
            state = ddnsState
        )
    }
}

package com.boss.ipv6ddns.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OkHttpProvider {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

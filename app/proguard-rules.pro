# ProGuard rules for IPv6DDNS release builds.

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Aliyun SDK
-keep class com.aliyun.** { *; }
-keep interface com.aliyun.** { *; }
-keep class com.aliyuncs.** { *; }
-keep interface com.aliyuncs.** { *; }
-keep class com.alibaba.fastjson.** { *; }
-keep class com.alibaba.fastjson2.** { *; }
-dontwarn com.aliyun.**
-dontwarn com.aliyuncs.**

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# Compose
-keep class androidx.compose.** { *; }

# App classes (Composables & data classes used by Moshi/serialization)
-keep class com.boss.ipv6ddns.** { *; }

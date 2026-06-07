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

# XmlPullParser safety nets
#
# The aliyun v2 SDK used to pull in pull-parser transitively (via
# darabonba-java-core -> aliyun-java-core -> dom4j), and the SDK's
# com.aliyun.tea.utils.* classes still reference org.xmlpull.v1.* in
# their bytecode. We no longer depend on the SDK, but other AndroidX
# libraries (notably androidx.work and androidx.security.crypto)
# can also reference org.xmlpull.v1 types in their optional code
# paths, so we keep these safety-net rules to satisfy R8 full mode
# in case any of those optional paths survive shrinking.
-dontwarn org.xmlpull.v1.**
-dontwarn kxml2.**
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.**
-keep,allowobfuscation,allowshrinking class org.xmlpull.v1.** { *; }
-keep,allowobfuscation,allowshrinking interface org.xmlpull.v1.** { *; }

# R8 full mode is strict about missing class references in optional
# code paths inside our remaining dependencies (OkHttp's optional
# Conscrypt integration, Coroutines debug agents, etc.). We never
# invoke those code paths from our app, so the missing classes are
# harmless.
-ignorewarnings

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# Compose
-keep class androidx.compose.** { *; }

# App classes (Composables & data classes used by Moshi/serialization)
-keep class com.boss.ipv6ddns.** { *; }

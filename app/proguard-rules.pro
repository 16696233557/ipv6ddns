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

# XmlPullParser conflict fix
# pull-parser:2.1.10 (transitive of aliyun-gateway-pop -> darabonba-java-core
# -> aliyun-java-core -> dom4j) ships an org.xmlpull.v1.XmlPullParser
# interface. Android's android.content.res.XmlResourceParser (framework,
# always present on the device's boot classpath) already implements that
# interface, so R8 full mode errors out with:
#   "Library class android.content.res.XmlResourceParser implements
#    program class org.xmlpull.v1.XmlPullParser"
#
# The dependency exclude in app/build.gradle.kts removes the offending
# pull-parser jar from the program classpath entirely (the framework's
# interface is the one that gets used at runtime). These -dontwarn
# rules are belt-and-braces: they silence R8's references to the
# org.xmlpull.v1.* and kxml2 packages that survive in other transitives'
# bytecode constants even after the exclude, so we don't get a
# follow-on "missing class" error from R8 full mode's strict typing.
-dontwarn org.xmlpull.v1.**
-dontwarn kxml2.**
-dontwarn org.kxml2.**
-dontwarn org.xmlpull.**
-keep,allowobfuscation,allowshrinking class org.xmlpull.v1.** { *; }
-keep,allowobfuscation,allowshrinking interface org.xmlpull.v1.** { *; }

# Aliyun SDK
-keep class com.aliyun.** { *; }
-keep interface com.aliyun.** { *; }
-keep class com.aliyuncs.** { *; }
-keep interface com.aliyuncs.** { *; }
-keep class com.alibaba.fastjson.** { *; }
-keep class com.alibaba.fastjson2.** { *; }
-dontwarn com.aliyun.**
-dontwarn com.aliyuncs.**
-dontwarn springfox.**
-dontwarn org.apache.http.**
-dontwarn com.alibaba.fastjson.support.springfox.**
-dontwarn com.alibaba.fastjson.support.jaxrs.**
-dontwarn com.alibaba.fastjson.support.spring.**
-dontwarn com.alibaba.fastjson.support.moneta.**
-dontwarn com.alibaba.fastjson.serializer.AwtCodec
-dontwarn com.alibaba.fastjson.serializer.GuavaCodec
-dontwarn com.alibaba.fastjson.serializer.MonetaCodec
-dontwarn java.awt.**
-dontwarn javax.servlet.**
-dontwarn javax.ws.rs.**
-dontwarn javax.money.**
-dontwarn com.google.common.collect.**
-dontwarn com.google.common.base.**
-dontwarn com.google.common.primitives.**
-dontwarn com.google.common.cache.**
-dontwarn com.google.common.hash.**
-dontwarn org.glassfish.jersey.**

# Aliyun v1 SDK on Android 14 workaround — see app/build.gradle.kts where
# the aliyun-java-sdk-alidns transitive org.apache.httpcomponents:httpclient
# is excluded to dodge NoSuchFieldError on AllowAllHostnameVerifier.INSTANCE.
# The Aliyun core 4.6.3 then falls back to java.net.http.HttpClient
# (API 33+).
#
# R8 full mode is strict about missing class references; we use
# -ignorewarnings to let fastjson's server-side glue (javax.servlet, AWT,
# Joda time, Guava, retrofit2, etc.) stay in the dex without each of those
# classes being on the runtime classpath. We never invoke those code paths
# from our app, so the missing classes are harmless.
-ignorewarnings

# WorkManager workers
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker

# Compose
-keep class androidx.compose.** { *; }

# App classes (Composables & data classes used by Moshi/serialization)
-keep class com.boss.ipv6ddns.** { *; }

# darabonba-java-core (transitive of alibabacloud-alidns20150109:4.0.12) ships
# `org.xmlpull.v1.XmlPullParser` as a program class, but Android's
# android.content.res.XmlResourceParser is a library class that already
# implements the same interface. R8's full mode flags this as
# "Library class implements program class" and refuses to compile. We
# keep darabonba's interface (so it can resolve references) and don't
# let R8 rewrite the platform's interface name.
-keep interface org.xmlpull.v1.XmlPullParser { *; }
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.xmlpull.v1.**
-dontwarn org.xmlpull.mxp1.**
-dontwarn org.kxml2.**

# aliyun-java-auth's SignAlgorithmHmacSM3 static initializer reaches into
# BouncyCastle (org.bouncycastle.*) for SM3 / SM3WithSM2 / SM4 cipher
# support. We pull in bcprov-jdk18on-1.78.1 transitively but R8 full
# mode strips unused classes by default; a missing class in the static
# initializer turns into ExceptionInInitializerError the first time
# AsyncClient (or anything in aliyun-java-auth) is touched. Keep the
# entire bcprov namespace — we don't care about dex size on a security-
# critical lib.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

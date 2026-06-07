plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.boss.ipv6ddns"
    compileSdk = 34

    // NOTE: we previously pulled in `org.apache.http.legacy` here, but that
    // stub library is incomplete on API 28+ — it lacks the static fields
    // (e.g. AllowAllHostnameVerifier.INSTANCE) that aliyun-java-sdk-alidns
    // references. We solve the real problem by depending on
    // org.apache.httpcomponents:httpclient-android in dependencies{} below.

    defaultConfig {
        applicationId = "com.boss.ipv6ddns"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = true
            // Even debug builds need R8 here: the aliyun-java-sdk-alidns
            // jar references org.apache.http.conn.ssl.AllowAllHostnameVerifier
            // .INSTANCE at runtime, and that field is absent in the
            // platform's org.apache.http.legacy stub on API 28+. Without
            // R8 the bytecode reference is never rewritten and the app
            // crashes on first aliyun API call. Release builds get this
            // for free; debug builds opt in here so the keep/rename rules
            // in proguard-rules.pro run during `:app:assembleDebug`.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
                "META-INF/INDEX.LIST",
                // Both darabonba-java-core and aliyun-java-auth (v2 SDK
                // transitives) ship a core.properties resource at the jar
                // root (not under META-INF/darabonba/); pick one (the
                // contents are identical) to avoid a merge collision.
                "core.properties"
            )
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    implementation(libs.kotlinx.coroutines.android)

    // Aliyun DNS v2 SDK (alibabacloud-alidns20150109:4.0.12). The v1 SDK
    // (aliyun-java-sdk-alidns) is unmaintained on Android 14: it hardcodes
    // Apache HttpClient 4.5.x, which has an `AllowAllHostnameVerifier.INSTANCE`
    // static field that is missing in Android 14's platform stub, so any
    // HTTPS call to api.aliyuncs.com raises NoSuchFieldError. The v2 SDK
    // uses aliyun-gateway-pop + darabonba-java-core internally; both target
    // Java 8 and work on Android without the legacy HttpClient dependency.
    //
    // We pull in the v2 jar directly and exclude the v1 aliyun-java-sdk-core
    // transitive (defined in [libraries] above as aliyun-java-sdk-core) which
    // would otherwise bring in bcprov-jdk15on-1.70 and conflict with v2's
    // bcprov-jdk18on-1.78.1 (BouncyCastle cannot have two versions in the
    // same dex).
    //
    // We also exclude the pull-parser transitive. dom4j (a transitive of v2
    // aliyun-java-core 0.3.5-beta) references org.xmlpull.v1.XmlPullParser,
    // and R8 full mode refuses to compile when a program class shadows a
    // library class that the framework implements — in this case Android's
    // android.content.res.XmlResourceParser already implements that exact
    // XmlPullParser interface, so the pull-parser jar's copy is dead weight
    // that breaks R8. The DNS codepath doesn't use SAXReader (it goes via
    // aliyun-gateway-pop + java.net.http.HttpClient), so excluding
    // pull-parser is safe. Proguard rules in app/proguard-rules.pro silence
    // any -dontwarn fallout from this exclude.
    implementation(libs.aliyun.java.sdk.alidns.v2) {
        exclude(group = "com.aliyun", module = "aliyun-java-sdk-core")
        exclude(group = "pull-parser", module = "pull-parser")
        exclude(group = "xpp3", module = "xpp3")
    }
    implementation(libs.fastjson)
}

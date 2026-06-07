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
}

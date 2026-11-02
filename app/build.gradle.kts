plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "id.omphub"
    compileSdk = 34

    defaultConfig {
        applicationId = "id.omphub"
        minSdk = 26
        // PENTING: targetSdk 28 disengaja. Android 10+ melarang exec dari folder data app
        // untuk app yang targetSdk >= 29, dan PRoot butuh itu. Sama seperti Termux.
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildFeatures { compose = true }

    // Aset rootfs sudah berformat .xz / .gz, tidak perlu dikompres lagi oleh aapt.
    androidResources { noCompress += listOf("xz", "gz") }

    packaging {
        // Binary di jniLibs harus diekstrak ke nativeLibraryDir supaya bisa di-exec.
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Di CI (ada KEYSTORE_FILE) pakai kunci tetap; build lokal tanpa keystore jatuh ke kunci debug.
            signingConfig =
                if (System.getenv("KEYSTORE_FILE") != null) signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.26.2")
    implementation("org.tukaani:xz:1.10")
}
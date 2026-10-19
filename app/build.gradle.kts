plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yusha.shottel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yusha.shottel"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fixed signing key so every build can update the previous install in place.
    // The keystore and password are supplied at build time (CI secrets / local file),
    // never committed. If absent, the build falls back to the default debug key.
    val keystore = file("kulumbi.p12")
    val ksPass = System.getenv("KS_PASS")
    val hasFixedKey = keystore.exists() && !ksPass.isNullOrBlank()

    signingConfigs {
        if (hasFixedKey) {
            create("fixed") {
                storeFile = keystore
                storePassword = ksPass
                keyAlias = "kulumbi"
                keyPassword = ksPass
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            if (hasFixedKey) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            if (hasFixedKey) signingConfig = signingConfigs.getByName("fixed")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

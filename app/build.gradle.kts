plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.hyperblocker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hyperblocker"
        minSdk = 8
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // LSPosed modules have no launcher activity — hide from app drawer
    androidResources {
        noCompress += ""
    }
}

dependencies {
    // XposedBridge API — compileOnly so it's not bundled in the APK
    compileOnly(libs.xposed.api)
}

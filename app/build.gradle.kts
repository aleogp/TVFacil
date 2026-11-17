plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.andres.tvfacil"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.andres.tvfacil"
        minSdk = 22
        targetSdk = 34
        versionCode = 6
        versionName = "6.0"
    }

    // Llave fija: así cada APK nuevo se instala encima del anterior sin desinstalar.
    signingConfigs {
        create("fija") {
            storeFile = file("tvfacil.keystore")
            storePassword = "tvfacil123"
            keyAlias = "tvfacil"
            keyPassword = "tvfacil123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fija")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // WireGuard oficial (solo el motor de túnel, sin la app)
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
}

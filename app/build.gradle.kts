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
        // La versión sale del número de compilación de GitHub Actions (igual que la etiqueta v7.N
        // del Release), para que la app sepa si hay una más nueva. Si cambias el 7 aquí,
        // cámbialo también en .github/workflows/build.yml (tag_name).
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + run
        versionName = "7.$run"

        // Secretos de GitHub (vacíos si no están): aviso de ayuda (ntfy) y señal de vida (healthchecks.io)
        val ntfy = (System.getenv("NTFY_TOPIC") ?: "").trim().replace("\"", "")
        val hc = (System.getenv("HC_PING_URL") ?: "").trim().replace("\"", "")
        buildConfigField("String", "NTFY_TOPIC", "\"$ntfy\"")
        buildConfigField("String", "HC_PING_URL", "\"$hc\"")
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

    buildFeatures {
        buildConfig = true
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

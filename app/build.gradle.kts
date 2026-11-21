plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.heitorcampos.androcargps"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.heitorcampos.androcargps"
        minSdk = 26          // Android 8.0+
        targetSdk = 34
        // No GitHub Actions cada build ganha um número novo (1.0.<n>), usado pela atualização do app.
        val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 100 + ciRun
        versionName = "1.0.$ciRun"
    }

    // Mesma chave em todos os builds (PC e GitHub): permite atualizar por cima sem desinstalar.
    signingConfigs {
        create("androcar") {
            storeFile = rootProject.file("keystore/androcar.jks")
            storePassword = System.getenv("ANDROCAR_STORE_PASSWORD") ?: "androcar-gps"
            keyAlias = "androcar"
            keyPassword = System.getenv("ANDROCAR_KEY_PASSWORD") ?: "androcar-gps"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("androcar")
        }
        debug {
            signingConfig = signingConfigs.getByName("androcar")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // QR code: gerar no celular e ler com a câmera do tablet
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

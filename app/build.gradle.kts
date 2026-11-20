plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clé de signature fixe : indispensable pour que les mises à jour s'installent par-dessus l'ancienne version.
// Le fichier est recréé par GitHub Actions à partir du secret MOONSUIVI_KEYSTORE.
val cleSignature = file("moonsuivi.jks")

android {
    namespace = "com.moonsuivi.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.moonsuivi.app"
        minSdk = 30          // Android 11
        targetSdk = 30       // Android 11
        // Numéro de version fourni automatiquement par GitHub Actions (numéro du build)
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    }

    signingConfigs {
        create("moon") {
            storeFile = cleSignature
            storePassword = "moonsuivi"
            keyAlias = "moonsuivi"
            keyPassword = "moonsuivi"
        }
    }

    buildTypes {
        debug {
            if (cleSignature.exists()) signingConfig = signingConfigs.getByName("moon")
        }
        release {
            isMinifyEnabled = false
            if (cleSignature.exists()) signingConfig = signingConfigs.getByName("moon")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Synchronisation périodique des e-mails
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // E-mail (IMAP / SMTP)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}

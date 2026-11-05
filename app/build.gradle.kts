plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.islamic.adhkar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.islamic.adhkar"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("release-key.jks")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "ZadAdhkar2026!"
                keyAlias = System.getenv("KEY_ALIAS") ?: "zad_key"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "ZadAdhkar2026!"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ksFile = file("release-key.jks")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}

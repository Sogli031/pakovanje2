plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Zajednički potpis: Desktop\programi\keystore
rootProject.file("../keystore/signing.gradle").takeIf { it.exists() }?.let { apply(from = it) }

android {
    namespace = "com.pakovanje2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pakovanje2"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
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
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
}

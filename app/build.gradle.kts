plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.dispensa.needlevoice"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.dispensa.needlevoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.0"
    }
    signingConfigs {
        create("needle") { storeFile = file("needle.jks"); storePassword = "needle2026"; keyAlias = "needle"; keyPassword = "needle2026" }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("needle") }
        getByName("release") { signingConfig = signingConfigs.getByName("needle"); isMinifyEnabled = false }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
}

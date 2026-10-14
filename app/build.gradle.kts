plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.dispensa.needlevoice"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.dispensa.needlevoice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-prototype"
    }
}

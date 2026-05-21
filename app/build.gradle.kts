plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.soundcore.cipher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soundcore.cipher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // La llave maestra de Square para correr JS nativo en Android
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
}

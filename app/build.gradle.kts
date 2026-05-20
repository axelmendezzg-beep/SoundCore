plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.soundcore.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.soundcore.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    
    // 🌐 Red y JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // 🎧 Motor de Audio en segundo plano (Media3)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    
    // 🔥 Coroutines para hilos asíncronos en el puente
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

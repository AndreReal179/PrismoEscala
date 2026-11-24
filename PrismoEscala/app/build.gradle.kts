plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.prismo.escala"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.prismo.escala"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../escala.keystore")
            storePassword = "prismoescala"
            keyAlias = "escala"
            keyPassword = "prismoescala"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

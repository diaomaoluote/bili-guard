plugins {
    id("com.android.application")
}

android {
    namespace = "com.biliguard.timer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.biliguard.timer"
        minSdk = 26
        targetSdk = 33
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

    lint {
        abortOnError = false
    }
}

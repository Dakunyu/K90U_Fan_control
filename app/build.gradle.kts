plugins {
    id("com.android.application")
}

android {
    namespace = "com.k90ultra.fancontrol"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.k90ultra.fancontrol"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

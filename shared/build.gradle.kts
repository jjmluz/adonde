plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The game's rules, state, maps, text and assets, used by both the TV app and the phone app.
android {
    namespace = "com.jjmluz.adonde.shared"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.activity:activity-compose:1.13.0")
    api("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    api("com.google.android.gms:play-services-maps:20.1.0")
    api(project(":core"))
}

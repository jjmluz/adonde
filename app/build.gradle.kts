import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Maps API key stays in local.properties, out of git.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.jjmluz.adonde"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jjmluz.adonde"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["mapsApiKey"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Sideloaded personal build: sign with the debug key so `installRelease` just works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Tells Android which languages the game has, so it can offer them in the app's language setting.
        generateLocaleConfig = true
        // Leaves out the libraries' other translations, which the game's own text doesn't have.
        localeFilters += listOf("en", "pt", "es", "fr")
    }
}

dependencies {
    implementation(project(":shared"))
}

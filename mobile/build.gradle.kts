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
    namespace = "com.jjmluz.adonde.mobile"
    compileSdk = 37

    defaultConfig {
        // The same id as the TV app, so the Maps key's package restriction covers both. They never share a
        // device, but installing one over the other replaces it.
        applicationId = "com.jjmluz.adonde"
        minSdk = 31
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
        generateLocaleConfig = true
        localeFilters += listOf("en", "pt", "es", "fr")
    }
}

dependencies {
    implementation(project(":shared"))
}

// Both apps have the same id, so a plain `./gradlew installRelease` would put the phone app over the TV app.
// The phone app installs only when asked for by name: `./gradlew :mobile:installRelease`.
val mobileRequested = gradle.startParameter.taskNames.any { it.startsWith(":mobile:") || it.startsWith("mobile:") }
tasks.matching { it.name.startsWith("install") }.configureEach {
    val requested = mobileRequested // A local copy, so the check doesn't hold on to the script.
    onlyIf("the phone app was asked for by name") { requested }
}

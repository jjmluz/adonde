plugins {
    kotlin("multiplatform")
}

// The rules and the place picker, in plain Kotlin: the Android apps use the JVM build through `shared`, and the
// iOS app links the AdondeCore framework.
kotlin {
    // Java 17 bytecode like the Android modules, built with whichever JDK runs Gradle.
    jvm {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "AdondeCore"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

// The Android modules' `testDebugUnitTest` is how the tests are run (see AGENTS.md); the rules' tests live here now.
tasks.register("testDebugUnitTest") {
    dependsOn("jvmTest")
}

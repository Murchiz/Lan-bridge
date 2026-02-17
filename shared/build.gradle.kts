import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // 1. Android Configuration (AGP 9.0+ KMP DSL)
    androidLibrary {
        namespace = "com.lanbridge.shared"
        compileSdk = 36
        minSdk = 26
        // 'compileOptions' are now handled by jvmToolchain below
    }

    // 2. Desktop Configuration
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // 3. Global Java Toolchain (Sets source/target compat for both Android & Desktop)
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform (Using explicit catalog libs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.extended)

            // Kotlin X
            implementation(libs.jetbrains.kotlinx.coroutines.core)
            implementation(libs.jetbrains.kotlinx.serialization.json)

            // Ktor
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.server.cio)
        }

        androidMain.dependencies {
            implementation(libs.jetbrains.kotlinx.coroutines.android)
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.jetbrains.kotlinx.coroutines.swing)
            }
        }
    }
}
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // 1. Android Configuration moves HERE
    androidLibrary {
        namespace = "com.lanbridge.shared"
        compileSdk = 36
        minSdk = 26
        // No 'compileOptions' needed here; jvmToolchain handles it.
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // 2. This controls Java compatibility for both Android and Desktop
    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            // 3. UPDATED DEPENDENCIES (No more 'compose.runtime', etc.)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.icons.extended) // Or use the one defined in Step 1
            
            implementation(libs.jetbrains.kotlinx.coroutines.core)
            implementation(libs.jetbrains.kotlinx.serialization.json)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.server.cio)
        }

        androidMain.dependencies {
            // Android-specific dependencies
        }
    }
}

// 4. REMOVED: The top-level android { } block is NOT allowed with this plugin.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid) // <--- CHANGED: Use Android plugin, not Multiplatform
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.lanbridge.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lanbridge.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Standard way to set Kotlin JVM target in an Android module
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// Move dependencies from 'sourceSets' to the top-level block
dependencies {
    implementation(projects.shared) // Access your shared module

    // Compose Multiplatform Dependencies (automatically map to Android variants)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.materialIconsExtended)
    
    // Android Specific Dependencies
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Previews & Tooling
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

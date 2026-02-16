plugins {
    alias(libs.plugins.androidApplication)
    // REMOVED: Kotlin Android plugin (now built-in to AGP 9)
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

    // With AGP 9, jvmTarget is automatically synced with 
    // targetCompatibility above, so no extra 'kotlinOptions' needed!
}

dependencies {
    implementation(projects.shared)
    
    // Compose Multiplatform libraries
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.materialIconsExtended)
    
    // Android-specific wrappers
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

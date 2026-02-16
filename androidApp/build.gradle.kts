plugins {
    alias(libs.plugins.androidApplication)
    // REMOVE: alias(libs.plugins.kotlinAndroid) <--- This is now built-in!
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
        // ...
    }

    // Since you aren't applying the Kotlin plugin, 
    // use the android-native way to set the JVM target
    kotlinOptions {
        jvmTarget = "17"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.shared)
    // ... rest of your dependencies
}

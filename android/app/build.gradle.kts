plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.you.secretvault"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.you.secretvault"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // Round 3 adds native code here:
        // externalNativeBuild { cmake { cppFlags += "" } }
    }

    buildTypes {
        // Deliberately weak: debuggable release so the app is easy to attack
        // while you learn. Flip minify/strip ON in Day 2 / Round 4.
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Round 3: uncomment to compile the C integrity check.
    // externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}

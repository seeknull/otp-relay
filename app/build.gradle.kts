import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.guru.otprelay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.guru.otprelay"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    // App Links verification pins the signing certificate, so release builds must be signed with
    // a stable key. The keystore lives outside the repo; override the paths in ~/.gradle or a
    // local gradle.properties. Without it, release builds are simply left unsigned.
    val keystorePath = (findProperty("otprelay.keystore") as String?)
        ?: "${System.getProperty("user.home")}/.android/otp-relay-release.keystore"

    signingConfigs {
        if (file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = (findProperty("otprelay.storePassword") as String?) ?: "otprelay"
                keyAlias = (findProperty("otprelay.keyAlias") as String?) ?: "otprelay"
                keyPassword = (findProperty("otprelay.keyPassword") as String?) ?: "otprelay"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // A real org.json on the JVM: the android.jar stub throws on every call.
    testImplementation("org.json:json:20250517")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

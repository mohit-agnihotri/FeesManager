// app/build.gradle.kts
// UPDATED: Added AI features (Gemini API), Glide, Supabase Storage

import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace   = "com.example.feesmanager"
    compileSdk  = 36

    defaultConfig {
        applicationId          = "com.example.feesmanager"
        minSdk                 = 26
        targetSdk              = 36
        versionCode            = 8          // v8 — Cashfree Easy Split migration
        versionName            = "8.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ── AI API Keys (read from local.properties) ─────────────────
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "GROQ_API_KEY",
            "\"${localProperties.getProperty("GROQ_API_KEY", "")}\"")

    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
    buildFeatures {
        viewBinding  = true
        buildConfig  = true
    }
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")

    // ── Transitive Dependency Stabilization ───────────────────────
    implementation("androidx.browser:browser") {
        version { strictly("1.8.0") }
    }

    // ── Google Sign In ──────────────────────────────────────────
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // ── Biometric ─────────────────────────────────────────────────
    implementation("androidx.biometric:biometric:1.1.0")

    // ── UPI Payments (Cashfree only) ────────────────────────
    
    // ── Cashfree Payment Gateway SDK (NEW) ───────────────────────────────────
    implementation("com.cashfree.pg:api:2.2.8")
    // ── MPAndroidChart ────────────────────────────────────────────
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ── iText PDF ─────────────────────────────────────────────────
    implementation("com.itextpdf:itextg:5.5.10")

    // ── ✅ NEW: Glide — profile image loading & caching ───────────
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // ── AndroidX ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // ── MVVM Architecture ──────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // ── EncryptedSharedPreferences ────────────────────────────────
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Supabase ──────────────────────────────────────────────────
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)      // ✅ for avatar uploads
    implementation(libs.ktor.client.android)

    // ── AI / Ktor JSON (for Gemini REST API + Image Search) ───────
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    implementation("io.ktor:ktor-client-logging:3.0.0")

    // ── Testing ───────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

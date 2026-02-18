// Edited: 2026-01-08
// Purpose: Use version catalog and pluginManagement to resolve plugins; apply Kotlin Compose compiler plugin per Kotlin 2.0 requirement; configure Koin + WorkManager + Retrofit + Room + Firebase deps.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

    // Code generation
    alias(libs.plugins.ksp)     // Room ONLY

    // Firebase configuration processing
    // alias(libs.plugins.google.services)
}

// Fix for: NoSuchMethodError: com.squareup.javapoet.ClassName.canonicalName()
// Some processors (Room/others) can pull older javapoet versions transitively.
// Force a compatible version ONLY on processor classpaths.
configurations.matching {
    it.name.contains("ksp", ignoreCase = true)
}.configureEach {
    resolutionStrategy.force("com.squareup:javapoet:1.13.0")
}

android {
    namespace = "com.example.ceylonqueuebuspulse"
    compileSdk = 35

    // Read API base URL and HTTP sync feature flag from Gradle properties, with defaults
    // Remove legacy backend configuration; Mongo is the only backend now.
    val mongoApiBaseUrl: String = providers.gradleProperty("MONGO_API_BASE_URL").orNull ?: "http://10.0.2.2:3000/"

    defaultConfig {
        applicationId = "com.example.ceylonqueuebuspulse"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Expose backend config to code
        buildConfigField("String", "MONGO_API_BASE_URL", "\"${mongoApiBaseUrl}\"")

        // TomTom API key from Gradle property (set in local.properties or gradle.properties)
        val tomtomApiKey: String = providers.gradleProperty("TOMTOM_API_KEY").orNull ?: ""
        buildConfigField("String", "TOMTOM_API_KEY", "\"${tomtomApiKey}\"")

        // Pass TomTom SDK key to manifest placeholder (for SDK initialization)
        manifestPlaceholders["TOMTOM_SDK_KEY"] = tomtomApiKey
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

}

// KSP arguments for Room (top-level block as required by the KSP Gradle plugin)
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Material3
    implementation(libs.androidx.material3)

    // Activity Compose
    implementation(libs.androidx.activity.compose)

    // Lifecycle + ViewModel Compose
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Google Play Services Location
    implementation(libs.play.services.location)

    // Room (KSP)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.converter.moshi)
    implementation(libs.moshi.kotlin)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Koin (DI)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)


    // Material icons (using version catalog would be ideal; keeping explicit for now)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // OkHttp logging (useful during auth bring-up)
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Unit testing
    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // (Optional) Kotlin test helpers that bridge to JUnit4.
    // Keep Kotlin version aligned via the Kotlin plugin (no hardcoded version here).
    testImplementation(kotlin("test-junit"))

    // (Optional but useful) Android instrumented test deps
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Compose runtime-livedata for observeAsState
    implementation("androidx.compose.runtime:runtime-livedata:1.9.0")

    // For map implementation
    // Pin to stable core-ktx that resolves from Google/Maven Central in most environments.
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // TomTom Maps SDK - include only when credentials are present or property enabled.
    val tomtomUser: String? = providers.gradleProperty("TOMTOM_REPO_USER").orNull ?: System.getenv("TOMTOM_REPO_USER")
    val tomtomPassword: String? = providers.gradleProperty("TOMTOM_REPO_PASSWORD").orNull ?: System.getenv("TOMTOM_REPO_PASSWORD")
    val tomtomEnabledProp = providers.gradleProperty("TOMTOM_SDK_ENABLED").orNull
    val hasTomtomCreds = !tomtomUser.isNullOrBlank() && !tomtomPassword.isNullOrBlank()
    if (hasTomtomCreds || tomtomEnabledProp == "true") {
        implementation("com.tomtom.online:sdk-maps:5.7.0")
    } else {
        // No TomTom credentials => rely on local stubs under com.tomtom.* for compilation.
        // Keep the stubs in the source tree (com.tomtom.*) so builds succeed without the SDK.
    }

    // Pull-to-refresh for Compose
    implementation("androidx.compose.material:material-pull-refresh:1.1.0")

}

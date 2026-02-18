// Edited: 2026-01-06
// Purpose: Declare plugin versions (AGP, Kotlin, KSP, Compose compiler) so module scripts can apply plugins without inline versions.

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    // Configure plugin versions here for the whole build
    plugins {
        id("com.android.application") version "8.13.2"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // TomTom SDK public repository (optional credentials supported)
        maven {
            url = uri("https://tomtom.jfrog.io/artifactory/tt-public-releases")

            // IMPORTANT: keep this repo scoped to TomTom artifacts only.
            // Otherwise Gradle may try to resolve standard AndroidX artifacts from JFrog first and fail with 401.
            content {
                includeGroupByRegex("com\\.tomtom(\\..*)?")
                includeGroup("com.tomtom.online")
            }

            // Optional credentials: set in user Gradle properties (e.g. C:\Users\<you>\.gradle\gradle.properties)
            // Properties: TOMTOM_REPO_USER and TOMTOM_REPO_PASSWORD
            val tomtomUser: String? = providers.gradleProperty("TOMTOM_REPO_USER").orNull
                ?: System.getenv("TOMTOM_REPO_USER")
            val tomtomPassword: String? = providers.gradleProperty("TOMTOM_REPO_PASSWORD").orNull
                ?: System.getenv("TOMTOM_REPO_PASSWORD")
            if (!tomtomUser.isNullOrBlank() && !tomtomPassword.isNullOrBlank()) {
                credentials {
                    username = tomtomUser
                    password = tomtomPassword
                }
            }
        }
    }
}

rootProject.name = "Ceylon Queue + Bus Pulse"
include(":app")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }

    plugins {
        kotlin("multiplatform").version(extra["kotlin.version"] as String) apply false
        id("org.jetbrains.compose").version(extra["compose.version"] as String) apply false
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String) apply false
    }
}

rootProject.name = "cert-checker"

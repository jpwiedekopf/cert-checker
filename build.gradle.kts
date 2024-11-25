@file:Suppress("unused")

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("dev.hydraulic.conveyor") version "1.12"
}

group = "net.wiedekopf"
version = "1.0.9"

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/compose/dev") }
    maven { url = uri("https://packages.jetbrains.team/maven/p/kpm/public/") }
}

val exposedVersion: String by project
val ktorVersion: String by project


kotlin {
    jvm {
        withJava()
    }
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }

    sourceSets {
        val jvmMain: KotlinSourceSet by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
                implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
                implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")
                implementation("org.xerial:sqlite-jdbc:3.47.0.0")
                implementation("org.slf4j:slf4j-simple:2.0.16")
                implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")
                implementation("net.harawata:appdirs:1.2.2")
                implementation("commons-validator:commons-validator:1.9.0")
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")
                implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window-243:0.26.2")

                // Conveyor API: Manage automatic updates.
                implementation("dev.hydraulic.conveyor:conveyor-control:1.1")
            }
        }
    }
}

dependencies {
    // Use the configurations created by the Conveyor plugin to tell Gradle/Conveyor where to find the artifacts for each platform.
    linuxAmd64(compose.desktop.linux_x64)
    macAmd64(compose.desktop.macos_x64)
    macAarch64(compose.desktop.macos_arm64)
    windowsAmd64(compose.desktop.windows_x64)
}

compose.desktop {
    application {
        mainClass = "net.wiedekopf.cert_checker.MainKt"
    }
}

// region Work around temporary Compose bugs.
configurations.all {
    attributes {
        // https://github.com/JetBrains/compose-jb/issues/1404#issuecomment-1146894731
        attribute(Attribute.of("ui", String::class.java), "awt")
    }
}
// endregion
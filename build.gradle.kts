// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin) and only guarantees KGP 2.2.10.
        // Pin a newer Kotlin Gradle plugin so the Kotlin compiler can read the metadata of
        // current libraries (Hilt 2.60, coroutines 1.11, ...).
        // https://developer.android.com/build/releases/agp-9-0-0-release-notes
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

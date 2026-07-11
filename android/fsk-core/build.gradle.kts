// Thin JVM library module that reuses the Tier 0 POC fsk-core sources
// (package com.spacedrop.tier0.fsk) so the main Android app can consume the
// exact same, live-verified FSK decoder/encoder without duplicating code.
//
// We deliberately source-include the POC main sources rather than remapping the
// POC Gradle project directly: the POC build uses jvmToolchain(21) + JUnit 5,
// which do not align with the app's Java 17 / Android build. Compiling the same
// sources here at jvmTarget 17 keeps the artifact Android-consumable and leaves
// the POC project untouched. See docs/architecture-review-2026-07-08.md (§2, §3a).

plugins {
    id("org.jetbrains.kotlin.jvm")
}

sourceSets {
    named("main") {
        java.srcDir("../../tools/tier0-acoustic-poc/kotlin/fsk-core/src/main/kotlin")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

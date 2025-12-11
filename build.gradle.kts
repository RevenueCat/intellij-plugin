/*
 * Copyright (c) 2025 RevenueCat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.intellij") version "1.17.2"
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.revenuecat"
version = "1.0.1"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("io.coil-kt.coil3:coil-compose:3.0.0-rc01")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.0-rc01")

    // Koog AI Agent Framework
    implementation("ai.koog:koog-agents:0.5.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Markdown rendering
    implementation("org.jetbrains:markdown:0.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
}

intellij {
    version.set("2024.2")
    type.set("IC") // IntelliJ IDEA Community Edition
    plugins.set(listOf())
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("253.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt", "**/spotless/**/*.kt")
        ktlint()
            .editorConfigOverride(
                mapOf(
                    "indent_size" to "2",
                    "continuation_indent_size" to "2",
                    "ktlint_standard_filename" to "disabled",
                    "ktlint_standard_max-line-length" to "disabled"
                )
            )
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
    format("kts") {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts", "**/spotless/**/*.kts")
        licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
    }
    format("xml") {
        target("**/*.xml")
        targetExclude("**/build/**/*.xml", "**/spotless/**/*.xml")
        licenseHeaderFile(rootProject.file("spotless/copyright.xml"), "(<[^!?])")
    }
}

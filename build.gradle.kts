plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

group = "com.artembotnev.openai"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.openai:openai-java:4.39.1")
    implementation("io.insert-koin:koin-core:3.5.6")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okio:okio:3.17.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(19)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.artembotnev.openai.MainKt"
    }
}
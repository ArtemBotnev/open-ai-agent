plugins {
    kotlin("jvm") version "2.3.21"
}

group = "com.artembotnev.openai.test"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.openai:openai-java:4.39.1")
    implementation("com.openai:openai-java-client-okhttp:4.39.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(19)
}

tasks.test {
    useJUnitPlatform()
}
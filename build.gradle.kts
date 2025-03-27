plugins {
    id("idea")
    id("maven-publish")

    // https://kotlinlang.org/docs/gradle-configure-project.html
    kotlin("jvm") version "2.1.0"
}

group = "io.github.warraft"

version = "1.9.7"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.jcraft:jzlib:1.1.3")
    implementation("org.apache.commons:commons-compress:1.26.0")
    implementation("com.github.eustas:CafeUndZopfli:5cdf283e67")
    implementation("org.tukaani:xz:1.9")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

tasks.test {
    useJUnitPlatform()
}


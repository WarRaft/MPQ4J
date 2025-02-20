plugins {
    java

    id("idea")
    id("jacoco")
    id("com.github.kt3k.coveralls") version "2.12.0"
    id("maven-publish")

    // https://kotlinlang.org/docs/gradle-configure-project.html
    kotlin("jvm") version "2.1.0"
}

group = "io.github.warraft"

version = "1.9.7"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

jacoco {
    toolVersion = "0.8.7"
}

dependencies {
    implementation("com.jcraft:jzlib:1.1.3")
    implementation("org.apache.commons:commons-compress:1.24.0")
    implementation("com.github.eustas:CafeUndZopfli:5cdf283e67")
    implementation("org.tukaani:xz:1.9")
    implementation("org.slf4j:slf4j-api:1.7.31")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    testImplementation("org.testng:testng:7.8.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks.test {
    useTestNG()
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
    }
}

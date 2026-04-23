import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("java")
}

group = "me.icicle"
version = "0.5.1"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/main/HytaleServer.jar"))
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
}

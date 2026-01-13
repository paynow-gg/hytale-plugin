plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
}

group = findProperty("pluginGroup") as String? ?: "gg.paynow"
version = findProperty("pluginVersion") as String? ?: "0.0.1"
description = findProperty("pluginDescription") as String? ?: "PayNow Hytale Plugin"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    compileOnly(files("libs/hytale-server.jar"))

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains:annotations:24.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 25
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()

        val props = mapOf(
            "group" to project.group,
            "version" to project.version,
            "description" to project.description
        )
        inputs.properties(props)
        
        filesMatching("manifest.json") {
            expand(props)
        }
    }

    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")

        relocate("com.google.gson", "gg.paynow.libs.gson")

        minimize()
    }
    
    // Configure tests
    test {
        useJUnitPlatform()
    }
    
    // Make build depend on shadowJar
    build {
        dependsOn(shadowJar)
    }
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

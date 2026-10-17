import org.gradle.kotlin.dsl.invoke
import java.util.Properties

plugins {
    id("java")
    id("java-library")

    id("dev.architectury.loom") version("1.11-SNAPSHOT") apply false
    id("architectury-plugin") version("3.4-SNAPSHOT")
    id("com.gradleup.shadow") version("8.3.6") apply false
    id("jacoco")
}

architectury {
    minecraft = property("minecraft_version") as String
}

// Load local gradle properties if they exist (for dev overrides)
val localPropertiesFile = file("gradle.properties.local")
if (localPropertiesFile.exists()) {
    val localProperties = Properties()
    localPropertiesFile.inputStream().use { localProperties.load(it) }
    (localProperties as Map<*, *>).forEach { (key, value) -> extra.set(key.toString(), value.toString()) }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    version = rootProject.property("mod_version") as String
    group = rootProject.property("maven_group") as String

    repositories {
        mavenCentral()
        maven { url = uri("https://maven.architectury.dev/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
    }

    dependencies {
        "implementation"("de.marhali:json5-java:${rootProject.property("json5_version")}")
    }

    configure<JavaPluginExtension> {
        withSourcesJar()
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    tasks {
        withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }
    }
}

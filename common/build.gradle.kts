plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
}

base {
    archivesName.set(rootProject.property("archives_base_name") as String)
}

val enabled_platforms: String by rootProject

architectury {
    common(*enabled_platforms.split(",").toTypedArray())
}

loom {
    silentMojangMappingsLicense()
}

tasks {
    named<Jar>("jar") {
        manifest.attributes("Automatic-Module-Name" to "multivillageselector.common")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${rootProject.property("minecraft_version")}:${rootProject.property("parchment_version")}@zip")
    })

    "modImplementation"("dev.architectury:architectury:${rootProject.property("architectury_version")}")

    compileOnly("org.spongepowered:mixin:0.8.5")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

    implementation("de.marhali:json5-java:3.0.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        maxHeapSize = "2G"

        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }

        if (System.getenv("CI") != null) {
            failFast = true
        }
    }
}

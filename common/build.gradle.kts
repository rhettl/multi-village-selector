plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("jacoco")
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

        finalizedBy("jacocoTestReport")
    }

    named<JacocoReport>("jacocoTestReport") {
        dependsOn(named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
            csv.required.set(false)
        }
        // Mixins need a running game to exercise; they are covered by integration runs, not unit tests
        classDirectories.setFrom(files(classDirectories.files.map {
            fileTree(it) { exclude("**/mixin/**") }
        }))
    }
}

jacoco {
    toolVersion = "0.8.12"
}

// Regression floor, not a target: much of the code (commands, scanner) needs a running game.
// Raise it as logic is extracted into pure, testable units.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    classDirectories.setFrom(tasks.named<JacocoReport>("jacocoTestReport").map { it.classDirectories })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.12".toBigDecimal()
            }
        }
    }
}

tasks.named("check") { dependsOn("jacocoTestCoverageVerification") }

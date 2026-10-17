import org.gradle.api.component.AdhocComponentWithVariants

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

base {
    archivesName.set("${rootProject.property("archives_base_name")}-fabric")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()
    runs {
        create("clientIntegration") {
            client()
            runDir("run-integration")
            ideConfigGenerated(true)
            name("Minecraft Client (Fabric, Integration)")
        }
        create("serverIntegration") {
            server()
            runDir("run-integration")
            ideConfigGenerated(true)
            name("Minecraft Server (Fabric, Integration)")
        }
    }
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    "modImplementation"("net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
    "modApi"("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric_api_version")}")
    "modApi"("dev.architectury:architectury-fabric:${rootProject.property("architectury_version")}")

    implementation(project(":common", configuration = "namedElements")) { isTransitive = false }
    "developmentFabric"(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(":common", configuration = "transformProductionFabric")) { isTransitive = false }

    shadowBundle("de.marhali:json5-java:${rootProject.property("json5_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation(project(":common"))
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.property("mod_version", project.property("mod_version"))
        filesMatching("fabric.mod.json") {
            expand(mapOf("mod_version" to project.property("mod_version")))
        }
    }

    shadowJar {
        exclude("architectury.common.json")
        configurations = listOf(shadowBundle)
        archiveClassifier.set("dev-shadow")
        relocate("de.marhali.json5", "com.rhett.multivillageselector.shadow.json5")
    }

    remapJar {
        // injectAccessWidener = true  // No .accesswidener file defined yet
        inputFile.set(shadowJar.flatMap { it.archiveFile })
        dependsOn(shadowJar)
        archiveBaseName.set("${rootProject.property("archives_base_name")}-fabric")
        archiveClassifier.set(null as String?)
    }

    jar {
        archiveClassifier.set("dev")
    }

    named<Jar>("sourcesJar") {
        val commonSources = project(":common").tasks.getByName<Jar>("sourcesJar")
        dependsOn(commonSources)
        from(commonSources.archiveFile.map { zipTree(it) })
    }
}

(components["java"] as AdhocComponentWithVariants)
    .withVariantsFromConfiguration(configurations["shadowRuntimeElements"]) { skip() }

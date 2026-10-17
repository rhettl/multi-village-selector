import org.gradle.api.component.AdhocComponentWithVariants
import java.net.URI

plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

base {
    archivesName.set("${rootProject.property("archives_base_name")}-neoforge")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    silentMojangMappingsLicense()
    runs {
        create("clientIntegration") {
            client()
            runDir("run-integration")
            ideConfigGenerated(true)
            name("Minecraft Client (NeoForge, Integration)")
        }
        create("serverIntegration") {
            server()
            runDir("run-integration")
            ideConfigGenerated(true)
            name("Minecraft Server (NeoForge, Integration)")
        }
    }
}

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

repositories {
    maven {
        name = "Sinytra"
        url = uri("https://maven.su5ed.dev/releases")
    }
}

// NeoForge 21.1.x ships asm-commons-9.8 transitively, but securejarhandler puts
// asm-commons-9.7 on the module path — BootstrapLauncher errors on the conflict.
// Strip 9.8 from the Loom-generated classpath so only the module-path 9.7 is used.
tasks.named("configureLaunch") {
    doLast {
        val cpFile = file(".gradle/loom-cache/forge_minecraft_classpath.txt")
        if (cpFile.exists()) {
            val filtered = cpFile.readLines().filter { !it.contains("org.ow2.asm") || "/9.8/" !in it }
            cpFile.writeText(filtered.joinToString("\n"))
        }
    }
}

// Dev-only mods (Connector + FFAPI) — downloaded directly to run/mods/
// Run: ./gradlew :neoforge:syncDevMods  to download/update them
tasks.register("syncDevMods") {
    val modsDir = file("run/mods")
    outputs.dir(modsDir)
    doLast {
        modsDir.mkdirs()
        val base = "https://maven.su5ed.dev/releases"

        val connVer = project.property("sinytra_version") as String
        val connVerEnc = connVer.replace("+", "%2B")
        val connFile = File(modsDir, "connector-${connVer}.jar")
        if (!connFile.exists()) {
            println("Downloading Connector ${connVer}...")
            URI("${base}/org/sinytra/connector/${connVerEnc}/connector-${connVerEnc}-full.jar")
                .toURL().openStream().use { it.copyTo(connFile.outputStream()) }
        }

        val ffapiVer = project.property("forgified_fabric_version") as String
        val ffapiVerEnc = ffapiVer.replace("+", "%2B")
        val ffapiFile = File(modsDir, "forgified-fabric-api-${ffapiVer}.jar")
        if (!ffapiFile.exists()) {
            println("Downloading Forgified Fabric API ${ffapiVer}...")
            URI("${base}/org/sinytra/forgified-fabric-api/forgified-fabric-api/${ffapiVerEnc}/forgified-fabric-api-${ffapiVerEnc}.jar")
                .toURL().openStream().use { it.copyTo(ffapiFile.outputStream()) }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft_version")}")
    mappings(loom.officialMojangMappings())
    neoForge("net.neoforged:neoforge:${rootProject.property("neoforge_version")}")
    "modApi"("dev.architectury:architectury-neoforge:${rootProject.property("architectury_version")}")

    implementation(project(":common", configuration = "namedElements")) { isTransitive = false }
    "developmentNeoForge"(project(":common", configuration = "namedElements")) { isTransitive = false }
    shadowBundle(project(":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    shadowBundle("de.marhali:json5-java:${rootProject.property("json5_version")}")
    forgeRuntimeLibrary("de.marhali:json5-java:${rootProject.property("json5_version")}")

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
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(mapOf("mod_version" to project.property("mod_version")))
        }
    }

    shadowJar {
        exclude("fabric.mod.json")
        exclude("architectury.common.json")
        exclude("multi_village_selector.mixins.json")
        exclude("multivillageselector-common-refmap.json")
        configurations = listOf(shadowBundle)
        archiveClassifier.set("dev-shadow")
        relocate("de.marhali.json5", "com.rhett.multivillageselector.shadow.json5")
    }

    remapJar {
        // injectAccessWidener = true  // No .accesswidener file defined yet
        inputFile.set(shadowJar.flatMap { it.archiveFile })
        dependsOn(shadowJar)
        archiveBaseName.set("${rootProject.property("archives_base_name")}-neoforge")
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

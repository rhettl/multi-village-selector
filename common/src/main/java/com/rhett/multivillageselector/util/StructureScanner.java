package com.rhett.multivillageselector.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rhett.multivillageselector.MVSCommon;
import com.rhett.multivillageselector.commands.model.CommandModels.*;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility class for scanning structures from registry, JARs, and datapacks.
 * Extracted from MVSCommands for reusability.
 */
public class StructureScanner {

    private static final Gson GSON = new Gson();
    private static final String MINECRAFT_VILLAGES_SET = "minecraft:villages";
    private static final int TARGET_AVERAGE_WEIGHT = 25;
    private static final int VANILLA_SPACING = 34;

public static Map<String, StructureInfo> scanRegistryForStructures(
    net.minecraft.server.MinecraftServer server
) {
    Map<String, StructureInfo> structures = new HashMap<>();
    net.minecraft.core.RegistryAccess registryAccess = server.registryAccess();
    net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetRegistry =
        registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
    net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
        registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

    // Get biome tags for all structures
    Map<ResourceLocation, String> structureToBiomeTag = new HashMap<>();
    for (var entry : structureRegistry.entrySet()) {
        ResourceLocation structureId = entry.getKey().location();
        net.minecraft.world.level.levelgen.structure.Structure structure = entry.getValue();

        try {
            var biomeHolderSet = structure.biomes();
            var tagKey = biomeHolderSet.unwrapKey();
            if (tagKey.isPresent()) {
                structureToBiomeTag.put(structureId, "#" + tagKey.get().location().toString());
            } else {
                structureToBiomeTag.put(structureId, "direct_biomes");
            }
        } catch (Exception e) {
            structureToBiomeTag.put(structureId, "error: " + e.getMessage());
        }
    }

    // Track which structures are in structure_sets
    Map<ResourceLocation, String> structureSetMembership = new HashMap<>();

    // Scan all structure_sets and populate StructureInfo
    for (var entry : structureSetRegistry.entrySet()) {
        ResourceLocation setId = entry.getKey().location();
        String setIdStr = setId.toString();
        net.minecraft.world.level.levelgen.structure.StructureSet structureSet = entry.getValue();

        for (var selectionEntry : structureSet.structures()) {
            var structureKey = selectionEntry.structure().unwrapKey();
            if (structureKey.isPresent()) {
                ResourceLocation structureId = structureKey.get().location();
                String structureIdStr = structureId.toString();
                int weight = selectionEntry.weight();

                // Track membership
                structureSetMembership.put(structureId, setIdStr);

                // Only process minecraft:villages structures
                if (setIdStr.equals(MINECRAFT_VILLAGES_SET)) {
                    String biomeTag = structureToBiomeTag.getOrDefault(structureId, "unknown");

                    // Filter out dimension-specific structures
                    if (isDimensionSpecific(structureIdStr, biomeTag)) {
                        continue;
                    }

                    StructureInfo info = structures.computeIfAbsent(structureIdStr, StructureInfo::new);
                    info.registryWeight = weight;
                    info.registrySet = setIdStr;
                    info.registryBiome = biomeTag;
                }
            }
        }
    }

    // Also add uncategorized structures (not in any structure_set)
    for (var entry : structureRegistry.entrySet()) {
        ResourceLocation structureId = entry.getKey().location();
        String structureIdStr = structureId.toString();

        if (!structureSetMembership.containsKey(structureId)) {
            String biomeTag = structureToBiomeTag.getOrDefault(structureId, "unknown");

            // Filter out dimension-specific structures
            if (isDimensionSpecific(structureIdStr, biomeTag)) {
                continue;
            }

            StructureInfo info = structures.computeIfAbsent(structureIdStr, StructureInfo::new);
            info.registrySet = "NONE (uncategorized)";
            info.registryBiome = biomeTag;
            // Note: No weight for uncategorized structures
        }
    }

    return structures;
}

/**
 * Scan JAR files for structures and return JarScanData
 * Stage 2: Collect JAR data (structure_set files + lithostitched modifiers)
 * Only includes structures in "villages" structure_sets or uncategorized structures
 */
public static JarScanData scanJarsForStructures(
    net.minecraft.server.MinecraftServer server
) {
    JarScanData result = new JarScanData();
    try {
        // Get all loaded mod containers via Architectury Platform API
        for (var mod : dev.architectury.platform.Platform.getMods()) {
            String modId = mod.getModId();
            var dataRoot = mod.findResource("data").orElse(null);

            try {
                // First, scan structure_set files to get weights and empty weights
                Map<String, StructureJarInfo> structureSetData = scanAllStructureSetFiles(dataRoot, modId, result.structureSetInfo);

                // Then, scan individual structure files and merge with structure_set data
                List<StructureJarInfo> jarStructures = scanAllStructureFiles(dataRoot, modId, structureSetData);

                // Populate StructureInfo map - FILTER to villages structure_sets or uncategorized only
                for (StructureJarInfo jarInfo : jarStructures) {
                    // Filter out dimension-specific structures
                    if (isDimensionSpecific(jarInfo.name, jarInfo.biomeTag)) {
                        continue;
                    }

                    // Filter: Only include if in a "villages" structure_set OR no structure_set
                    boolean isInVillagesSet = jarInfo.structureSet != null &&
                                              jarInfo.structureSet.endsWith(":villages");
                    boolean isUncategorized = jarInfo.structureSet == null;

                    if (isInVillagesSet || isUncategorized) {
                        StructureInfo info = result.structures.computeIfAbsent(jarInfo.name, StructureInfo::new);

                        // Populate JAR data
                        info.jarWeight = jarInfo.weight;
                        info.jarSet = jarInfo.structureSet != null ? jarInfo.structureSet : "NONE (uncategorized)";
                        info.jarBiome = jarInfo.biomeTag;
                        info.jarSource = jarInfo.foundInStructureSet ? "structure_set" : "structure_file";
                    }
                }

            } catch (Exception e) {
                MVSCommon.LOGGER.warn("Failed to scan mod {}: {}", modId, e.getMessage());
            }
        }

        // ALSO scan .connector directory for Fabric mods loaded via Sinytra Connector
        scanConnectorDatapacksForStructures(server, result.structures, result.structureSetInfo);

    } catch (Exception e) {
        MVSCommon.LOGGER.error("Error scanning mod JARs", e);
    }

    return result;
}

/**
 * Scan .connector/data directory for Fabric mods and populate StructureInfo map
 */
public static void scanConnectorDatapacksForStructures(
    net.minecraft.server.MinecraftServer server,
    Map<String, StructureInfo> structures,
    Map<String, StructureSetInfo> structureSetInfo
) {
    try {
        Path gameDir = server.getServerDirectory();
        Path connectorDir = gameDir.resolve("mods/.connector/data");

        if (!java.nio.file.Files.exists(connectorDir)) {
            return;
        }

        // Scan structure_set files first
        Map<String, StructureJarInfo> connectorStructureSets = new HashMap<>();
        java.nio.file.Files.walk(connectorDir)
            .filter(path -> path.toString().contains("worldgen/structure_set"))
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = connectorDir.relativize(path).toString();
                    String[] parts = relativePath.split("/");

                    if (parts.length >= 4) {
                        String namespace = parts[0];
                        String setName = path.getFileName().toString().replace(".json", "");
                        String setId = namespace + ":" + setName;

                        // Parse JSON using Gson
                        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                        if (root.has("structures")) {
                            JsonArray jarStructures = root.getAsJsonArray("structures");

                            for (JsonElement element : jarStructures) {
                                JsonObject structureEntry = element.getAsJsonObject();

                                if (structureEntry.has("structure")) {
                                    String structureId = structureEntry.get("structure").getAsString();
                                    Integer weight = structureEntry.has("weight") ?
                                        structureEntry.get("weight").getAsInt() : null;

                                    // Store - figure out mod from structure ID
                                    String modId = structureId.contains(":") ? structureId.split(":")[0] : "unknown";
                                    StructureJarInfo info = connectorStructureSets.computeIfAbsent(structureId,
                                        k -> new StructureJarInfo(k, modId));
                                    info.structureSet = setId;
                                    info.weight = weight;
                                    info.foundInStructureSet = true;
                                } else if (structureEntry.has("weight")) {
                                    // Empty entry! Track empty weight for this structure_set
                                    Integer emptyWeight = structureEntry.get("weight").getAsInt();
                                    StructureSetInfo setInfo = structureSetInfo.computeIfAbsent(setId,
                                        k -> new StructureSetInfo(k));
                                    setInfo.emptyWeight = emptyWeight;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    MVSCommon.LOGGER.warn("[JAR-SCAN] Failed to parse connector structure_set: {}", e.getMessage());
                }
            });

        // Now scan structure files and merge
        java.nio.file.Files.walk(connectorDir)
            .filter(path -> path.toString().contains("worldgen/structure/"))
            .filter(path -> !path.toString().contains("structure_set"))
            .filter(path -> !path.toString().contains("/tags/"))
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = connectorDir.relativize(path).toString();

                    String[] parts = relativePath.split("/");
                    if (parts.length >= 4) {
                        String namespace = parts[0];
                        StringBuilder structurePathBuilder = new StringBuilder();
                        for (int i = 3; i < parts.length; i++) {
                            if (i > 3) structurePathBuilder.append("/");
                            structurePathBuilder.append(parts[i]);
                        }
                        String structurePath = structurePathBuilder.toString().replace(".json", "");
                        String structureId = namespace + ":" + structurePath;

                        // Get or create info
                        StructureJarInfo jarInfo = connectorStructureSets.get(structureId);
                        if (jarInfo == null) {
                            String modId = namespace;
                            jarInfo = new StructureJarInfo(structureId, modId);
                        }

                        // Extract biome tag
                        if (content.contains("\"biomes\"")) {
                            int biomeStart = content.indexOf("\"biomes\"");
                            int colonPos = content.indexOf(":", biomeStart);
                            if (colonPos > biomeStart) {
                                int biomeEnd = content.indexOf(",", colonPos);
                                if (biomeEnd == -1) biomeEnd = content.indexOf("}", colonPos);
                                if (biomeEnd > colonPos) {
                                    String biomeInfo = content.substring(colonPos + 1, Math.min(biomeEnd, colonPos + 100));
                                    biomeInfo = biomeInfo.replace("\n", " ").replace("\"", "").replace("[", "").replace("]", "").trim();
                                    jarInfo.biomeTag = biomeInfo;
                                }
                            }
                        }

                        jarInfo.foundInStructureFile = true;

                        // Filter out dimension-specific structures
                        if (isDimensionSpecific(structureId, jarInfo.biomeTag)) {
                            return;
                        }

                        // Filter: Only include if in a "villages" structure_set OR no structure_set
                        boolean isInVillagesSet = jarInfo.structureSet != null &&
                                                  jarInfo.structureSet.endsWith(":villages");
                        boolean isUncategorized = jarInfo.structureSet == null;

                        if (isInVillagesSet || isUncategorized) {
                            // Add to appropriate StructureInfo
                            StructureInfo info = structures.computeIfAbsent(structureId, StructureInfo::new);
                            info.jarWeight = jarInfo.weight;
                            info.jarSet = jarInfo.structureSet != null ? jarInfo.structureSet : "NONE (uncategorized)";
                            info.jarBiome = jarInfo.biomeTag;
                            info.jarSource = jarInfo.foundInStructureSet ? "structure_set" : "structure_file";
                        }
                    }
                } catch (Exception e) {
                    // Skip
                }
            });

    } catch (Exception e) {
        MVSCommon.LOGGER.warn("[JAR-SCAN] Error scanning .connector directory: {}", e.getMessage());
    }
}

/**
 * Initialize vanilla village structures (hardcoded - JAR scanning unreliable).
 * Returns new ScanResult with vanilla villages pre-populated.
 */
public static ScanResult initializeVanillaVillages() {
    ScanResult result = new ScanResult();

    String[] vanillaVillages = {
        "minecraft:village_plains",
        "minecraft:village_desert",
        "minecraft:village_savanna",
        "minecraft:village_snowy",
        "minecraft:village_taiga"
    };
    String[] vanillaBiomes = {
        "#minecraft:has_structure/village_plains",
        "#minecraft:has_structure/village_desert",
        "#minecraft:has_structure/village_savanna",
        "#minecraft:has_structure/village_snowy",
        "#minecraft:has_structure/village_taiga"
    };

    for (int i = 0; i < vanillaVillages.length; i++) {
        StructureInfo info = new StructureInfo(vanillaVillages[i]);
        info.registryWeight = 1;
        info.registrySet = MINECRAFT_VILLAGES_SET;
        info.registryBiome = vanillaBiomes[i];
        info.jarWeight = 1;
        info.jarSet = MINECRAFT_VILLAGES_SET;
        info.jarBiome = vanillaBiomes[i];
        info.jarSource = "hardcoded";
        result.allStructures.put(vanillaVillages[i], info);
    }

    // Add structure_set info for minecraft:villages
    StructureSetInfo vanillaSetInfo = new StructureSetInfo(MINECRAFT_VILLAGES_SET);
    vanillaSetInfo.emptyWeight = 0;
    result.structureSetInfo.put(MINECRAFT_VILLAGES_SET, vanillaSetInfo);

    return result;
}

/**
 * Unified structure scanning with clear separation of concerns:
 * 1. Collect ALL structures from all sources (no filtering)
 * 2. Merge into unified registry
 * 3. Filter for village-related structures
 * 4. Categorize into suggestion sets
 */
public static ScanResult scanAllStructures(net.minecraft.server.MinecraftServer server) {
    // ============================================================
    // PHASE 1: COLLECTION (no filtering, collect everything)
    // ============================================================

    // Pre-populate vanilla villages (hardcoded - JAR scanning unreliable for vanilla)
    ScanResult result = initializeVanillaVillages();

    // Scan registry - ALL structures with their active runtime state
    Map<String, StructureInfo> registryStructures = scanRegistryComplete(server);

    // Scan JARs - ALL structures from mod definition files
    JarScanData jarData = scanJarsComplete(server);

    // Scan datapacks - ALL structures from user datapacks
    JarScanData datapackData = scanDatapacksComplete(server);

    // ============================================================
    // PHASE 2: MERGE into unified registry
    // ============================================================

    result = mergeRegistryData(result, registryStructures, jarData);
    result = mergeRegistryData(result, new HashMap<>(), datapackData); // Merge datapacks (no registry data)

    // ============================================================
    // PHASE 3: FILTER for village-related structures
    // ============================================================

    Map<String, StructureInfo> villageStructures = filterVillageStructures(result.allStructures);

    // ============================================================
    // PHASE 4: CATEGORIZE into suggestion sets
    // ============================================================

    result = categorizeStructures(result, villageStructures);

    return result;
}

// ============================================================
// PHASE 1: COLLECTION METHODS (no filtering)
// ============================================================

/**
 * Scan registry for ALL structures (no filtering).
 * Collects runtime state: weights, structure_sets, biome tags.
 */
private static Map<String, StructureInfo> scanRegistryComplete(net.minecraft.server.MinecraftServer server) {
    Map<String, StructureInfo> structures = new HashMap<>();
    net.minecraft.core.RegistryAccess registryAccess = server.registryAccess();
    net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetRegistry =
        registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE_SET);
    net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structureRegistry =
        registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

    // Get biome tags for all structures
    Map<ResourceLocation, String> structureToBiomeTag = new HashMap<>();
    for (var entry : structureRegistry.entrySet()) {
        ResourceLocation structureId = entry.getKey().location();
        net.minecraft.world.level.levelgen.structure.Structure structure = entry.getValue();

        try {
            var biomeHolderSet = structure.biomes();
            var tagKey = biomeHolderSet.unwrapKey();
            if (tagKey.isPresent()) {
                structureToBiomeTag.put(structureId, "#" + tagKey.get().location().toString());
            } else {
                structureToBiomeTag.put(structureId, "direct_biomes");
            }
        } catch (Exception e) {
            structureToBiomeTag.put(structureId, "error: " + e.getMessage());
        }
    }

    // Track which structures are in structure_sets
    Map<ResourceLocation, String> structureSetMembership = new HashMap<>();

    // Scan all structure_sets and populate StructureInfo (NO FILTERING)
    for (var entry : structureSetRegistry.entrySet()) {
        ResourceLocation setId = entry.getKey().location();
        String setIdStr = setId.toString();
        net.minecraft.world.level.levelgen.structure.StructureSet structureSet = entry.getValue();

        for (var selectionEntry : structureSet.structures()) {
            var structureKey = selectionEntry.structure().unwrapKey();
            if (structureKey.isPresent()) {
                ResourceLocation structureId = structureKey.get().location();
                String structureIdStr = structureId.toString();
                int weight = selectionEntry.weight();

                // Track membership
                structureSetMembership.put(structureId, setIdStr);

                // Collect ALL structures (no filtering)
                String biomeTag = structureToBiomeTag.getOrDefault(structureId, "unknown");
                StructureInfo info = structures.computeIfAbsent(structureIdStr, StructureInfo::new);
                info.registryWeight = weight;
                info.registrySet = setIdStr;
                info.registryBiome = biomeTag;
            }
        }
    }

    // Also add uncategorized structures (not in any structure_set)
    for (var entry : structureRegistry.entrySet()) {
        ResourceLocation structureId = entry.getKey().location();
        String structureIdStr = structureId.toString();

        if (!structureSetMembership.containsKey(structureId)) {
            String biomeTag = structureToBiomeTag.getOrDefault(structureId, "unknown");

            // Collect ALL structures (no filtering)
            StructureInfo info = structures.computeIfAbsent(structureIdStr, StructureInfo::new);
            info.registrySet = "NONE (uncategorized)";
            info.registryBiome = biomeTag;
            // Note: No weight for uncategorized structures
        }
    }

    return structures;
}

/**
 * Scan JAR files for ALL structures (no filtering).
 * Collects mod definitions: weights, structure_sets, biome tags.
 */
private static JarScanData scanJarsComplete(net.minecraft.server.MinecraftServer server) {
    JarScanData result = new JarScanData();
    try {
        // Get all loaded mod containers via Architectury Platform API
        for (var mod : dev.architectury.platform.Platform.getMods()) {
            String modId = mod.getModId();
            var dataRoot = mod.findResource("data").orElse(null);

            try {
                // First, scan structure_set files to get weights and empty weights
                Map<String, StructureJarInfo> structureSetData = scanAllStructureSetFiles(dataRoot, modId, result.structureSetInfo);

                // Then, scan individual structure files and merge with structure_set data
                List<StructureJarInfo> jarStructures = scanAllStructureFiles(dataRoot, modId, structureSetData);

                // Populate StructureInfo map - COLLECT ALL (no filtering)
                for (StructureJarInfo jarInfo : jarStructures) {
                    StructureInfo info = result.structures.computeIfAbsent(jarInfo.name, StructureInfo::new);

                    // Populate JAR data
                    info.jarWeight = jarInfo.weight;
                    info.jarSet = jarInfo.structureSet != null ? jarInfo.structureSet : "NONE (uncategorized)";
                    info.jarBiome = jarInfo.biomeTag;
                    info.jarSource = jarInfo.foundInStructureSet ? "structure_set" : "structure_file";
                }

            } catch (Exception e) {
                MVSCommon.LOGGER.warn("Failed to scan mod {}: {}", modId, e.getMessage());
            }
        }

        // ALSO scan .connector directory for Fabric mods loaded via Sinytra Connector
        scanConnectorDatapacksForStructures(server, result.structures, result.structureSetInfo);

    } catch (Exception e) {
        MVSCommon.LOGGER.error("Error scanning mod JARs", e);
    }

    return result;
}

/**
 * Scan datapack ZIP files for ALL structures (no filtering).
 * Scans <world>/datapacks/*.zip for structure definitions.
 */
private static JarScanData scanDatapacksComplete(net.minecraft.server.MinecraftServer server) {
    JarScanData result = new JarScanData();
    try {
        // Get world-specific datapacks directory
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        Path datapacksDir = worldDir.resolve("datapacks");

        if (!java.nio.file.Files.exists(datapacksDir)) {
            return result; // No datapacks directory
        }

        // Collect all datapacks, deduplicating by name (directories take priority over ZIPs)
        Map<String, Path> datapacksByName = new HashMap<>();

        try (Stream<Path> paths = java.nio.file.Files.list(datapacksDir)) {
            paths.forEach(path -> {
                String fileName = path.getFileName().toString();
                String baseName = fileName.endsWith(".zip") ?
                    fileName.substring(0, fileName.length() - 4) : fileName;

                boolean isZip = fileName.endsWith(".zip");
                boolean isDir = java.nio.file.Files.isDirectory(path);

                // Check if it's a valid datapack
                boolean isValid = false;
                if (isZip) {
                    isValid = true; // Assume ZIPs are valid
                } else if (isDir) {
                    Path packMeta = path.resolve("pack.mcmeta");
                    isValid = java.nio.file.Files.exists(packMeta);
                }

                if (isValid) {
                    Path existing = datapacksByName.get(baseName);
                    if (existing == null) {
                        // First entry for this name
                        datapacksByName.put(baseName, path);
                    } else {
                        // Duplicate name - prioritize directory over ZIP
                        boolean existingIsDir = java.nio.file.Files.isDirectory(existing);
                        if (isDir && !existingIsDir) {
                            // Replace ZIP with directory
                            datapacksByName.put(baseName, path);
                            MVSCommon.LOGGER.info(
                                "Datapack '{}': Found both directory and ZIP, using directory (ignoring ZIP)",
                                baseName);
                        } else if (!isDir && existingIsDir) {
                            // Keep directory, ignore ZIP
                            MVSCommon.LOGGER.info(
                                "Datapack '{}': Found both directory and ZIP, using directory (ignoring ZIP)",
                                baseName);
                        }
                    }
                }
            });
        }

        // Scan deduplicated datapacks
        for (Path datapackPath : datapacksByName.values()) {
            try {
                if (datapackPath.toString().endsWith(".zip")) {
                    scanDatapackZip(datapackPath, result);
                } else {
                    scanDatapackDirectory(datapackPath, result);
                }
            } catch (Exception e) {
                MVSCommon.LOGGER.warn("Failed to scan datapack {}: {}",
                    datapackPath.getFileName(), e.getMessage());
            }
        }

    } catch (Exception e) {
        MVSCommon.LOGGER.error("Error scanning datapacks", e);
    }

    return result;
}

/**
 * Scan a single datapack ZIP file for structures.
 */
private static void scanDatapackZip(Path zipPath, JarScanData result) throws Exception {
    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
        // Extract namespace from first data/ entry
        String namespace = "unknown";
        var entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("data/") && name.split("/").length > 1) {
                namespace = name.split("/")[1];
                break;
            }
        }

        final String finalNamespace = namespace;

        // First pass: Scan structure_set files
        Map<String, StructureJarInfo> structureSetData = new HashMap<>();
        entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().contains("worldgen/structure_set") && entry.getName().endsWith(".json")) {
                try (InputStream is = zipFile.getInputStream(entry)) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String setName = entry.getName().substring(entry.getName().lastIndexOf("/") + 1).replace(".json", "");
                    String setId = finalNamespace + ":" + setName;

                    JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                    if (root.has("structures")) {
                        JsonArray structures = root.getAsJsonArray("structures");
                        for (JsonElement element : structures) {
                            JsonObject structureEntry = element.getAsJsonObject();
                            if (structureEntry.has("structure")) {
                                String structureId = structureEntry.get("structure").getAsString();
                                Integer weight = structureEntry.has("weight") ?
                                    structureEntry.get("weight").getAsInt() : null;

                                StructureJarInfo info = structureSetData.computeIfAbsent(structureId,
                                    k -> new StructureJarInfo(k, k.contains(":") ? k.split(":")[0] : finalNamespace));
                                info.structureSet = setId;
                                info.weight = weight;
                                info.foundInStructureSet = true;
                            } else if (structureEntry.has("weight")) {
                                // Empty entry
                                Integer emptyWeight = structureEntry.get("weight").getAsInt();
                                StructureSetInfo setInfo = result.structureSetInfo.computeIfAbsent(setId,
                                    k -> new StructureSetInfo(k));
                                setInfo.emptyWeight = emptyWeight;
                            }
                        }
                    }

                    // Extract spacing/separation from placement
                    if (root.has("placement")) {
                        JsonObject placement = root.getAsJsonObject("placement");
                        StructureSetInfo setInfo = result.structureSetInfo.computeIfAbsent(setId,
                            k -> new StructureSetInfo(k));

                        if (placement.has("spacing")) {
                            setInfo.spacing = placement.get("spacing").getAsInt();
                        }
                        if (placement.has("separation")) {
                            setInfo.separation = placement.get("separation").getAsInt();
                        }
                    }
                } catch (Exception e) {
                    MVSCommon.LOGGER.debug("Failed to parse structure_set in datapack: {}", e.getMessage());
                }
            }
        }

        // Second pass: Scan structure files
        entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.contains("worldgen/structure/") &&
                !name.contains("structure_set") &&
                !name.contains("/tags/") &&
                name.endsWith(".json")) {

                try (InputStream is = zipFile.getInputStream(entry)) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);

                    // Extract structure ID from path
                    String[] parts = name.split("/");
                    StringBuilder structurePathBuilder = new StringBuilder();
                    boolean foundStructure = false;
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("structure")) {
                            foundStructure = true;
                            continue;
                        }
                        if (foundStructure && i < parts.length) {
                            if (structurePathBuilder.length() > 0) structurePathBuilder.append("/");
                            structurePathBuilder.append(parts[i]);
                        }
                    }
                    String structurePath = structurePathBuilder.toString().replace(".json", "");
                    String structureId = finalNamespace + ":" + structurePath;

                    // Get or create info
                    StructureJarInfo info = structureSetData.get(structureId);
                    if (info == null) {
                        info = new StructureJarInfo(structureId, finalNamespace);
                    }

                    // Extract biome tag
                    if (content.contains("\"biomes\"")) {
                        int biomeStart = content.indexOf("\"biomes\"");
                        int colonPos = content.indexOf(":", biomeStart);
                        if (colonPos > biomeStart) {
                            int biomeEnd = content.indexOf(",", colonPos);
                            if (biomeEnd == -1) biomeEnd = content.indexOf("}", colonPos);
                            if (biomeEnd > colonPos) {
                                String biomeInfo = content.substring(colonPos + 1, Math.min(biomeEnd, colonPos + 100));
                                biomeInfo = biomeInfo.replace("\n", " ").replace("\"", "").replace("[", "").replace("]", "").trim();
                                info.biomeTag = biomeInfo;
                            }
                        }
                    }

                    info.foundInStructureFile = true;

                    // Add to result
                    StructureInfo structureInfo = result.structures.computeIfAbsent(structureId, StructureInfo::new);
                    structureInfo.jarWeight = info.weight;
                    structureInfo.jarSet = info.structureSet != null ? info.structureSet : "NONE (uncategorized)";
                    structureInfo.jarBiome = info.biomeTag;
                    structureInfo.jarSource = info.foundInStructureSet ? "structure_set" : "structure_file";
                } catch (Exception e) {
                    MVSCommon.LOGGER.debug("Failed to parse structure in datapack: {}", e.getMessage());
                }
            }
        }
    }
}

/**
 * Scan an unpacked datapack directory for structures.
 */
private static void scanDatapackDirectory(Path datapackDir, JarScanData result) throws Exception {
    Path dataDir = datapackDir.resolve("data");
    if (!java.nio.file.Files.exists(dataDir)) {
        return; // No data directory
    }

    // Extract namespace from first subdirectory in data/
    String namespace = "unknown";
    try (Stream<Path> namespaceDirs = java.nio.file.Files.list(dataDir)) {
        namespace = namespaceDirs
            .filter(java.nio.file.Files::isDirectory)
            .map(p -> p.getFileName().toString())
            .findFirst()
            .orElse("unknown");
    }

    final String finalNamespace = namespace;

    // First pass: Scan structure_set files
    Map<String, StructureJarInfo> structureSetData = new HashMap<>();
    Path structureSetDir = dataDir.resolve(finalNamespace).resolve("worldgen/structure_set");
    if (java.nio.file.Files.exists(structureSetDir)) {
        try (Stream<Path> structureSetFiles = java.nio.file.Files.walk(structureSetDir)) {
            structureSetFiles
                .filter(path -> path.toString().endsWith(".json"))
                .forEach(path -> {
                    try {
                        String content = java.nio.file.Files.readString(path);
                        String setName = path.getFileName().toString().replace(".json", "");
                        String setId = finalNamespace + ":" + setName;

                        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                        if (root.has("structures")) {
                            JsonArray structures = root.getAsJsonArray("structures");
                            for (JsonElement element : structures) {
                                JsonObject structureEntry = element.getAsJsonObject();
                                if (structureEntry.has("structure")) {
                                    String structureId = structureEntry.get("structure").getAsString();
                                    Integer weight = structureEntry.has("weight") ?
                                        structureEntry.get("weight").getAsInt() : null;

                                    StructureJarInfo info = structureSetData.computeIfAbsent(structureId,
                                        k -> new StructureJarInfo(k, k.contains(":") ? k.split(":")[0] : finalNamespace));
                                    info.structureSet = setId;
                                    info.weight = weight;
                                    info.foundInStructureSet = true;
                                } else if (structureEntry.has("weight")) {
                                    // Empty entry
                                    Integer emptyWeight = structureEntry.get("weight").getAsInt();
                                    StructureSetInfo setInfo = result.structureSetInfo.computeIfAbsent(setId,
                                        k -> new StructureSetInfo(k));
                                    setInfo.emptyWeight = emptyWeight;
                                }
                            }
                        }

                        // Extract spacing/separation from placement
                        if (root.has("placement")) {
                            JsonObject placement = root.getAsJsonObject("placement");
                            StructureSetInfo setInfo = result.structureSetInfo.computeIfAbsent(setId,
                                k -> new StructureSetInfo(k));

                            if (placement.has("spacing")) {
                                setInfo.spacing = placement.get("spacing").getAsInt();
                            }
                            if (placement.has("separation")) {
                                setInfo.separation = placement.get("separation").getAsInt();
                            }
                        }
                    } catch (Exception e) {
                        MVSCommon.LOGGER.debug("Failed to parse structure_set in datapack directory: {}", e.getMessage());
                    }
                });
        }
    }

    // Second pass: Scan structure files
    Path structureDir = dataDir.resolve(finalNamespace).resolve("worldgen/structure");
    if (java.nio.file.Files.exists(structureDir)) {
        try (Stream<Path> structureFiles = java.nio.file.Files.walk(structureDir)) {
            structureFiles
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> !path.toString().contains("structure_set"))
                .filter(path -> !path.toString().contains("/tags/"))
                .forEach(path -> {
                    try {
                        String content = java.nio.file.Files.readString(path);

                        // Extract structure ID from path
                        Path relativePath = structureDir.relativize(path);
                        String structurePath = relativePath.toString().replace(".json", "").replace("\\", "/");
                        String structureId = finalNamespace + ":" + structurePath;

                        // Get or create info
                        StructureJarInfo info = structureSetData.get(structureId);
                        if (info == null) {
                            info = new StructureJarInfo(structureId, finalNamespace);
                        }

                        // Extract biome tag
                        if (content.contains("\"biomes\"")) {
                            int biomeStart = content.indexOf("\"biomes\"");
                            int colonPos = content.indexOf(":", biomeStart);
                            if (colonPos > biomeStart) {
                                int biomeEnd = content.indexOf(",", colonPos);
                                if (biomeEnd == -1) biomeEnd = content.indexOf("}", colonPos);
                                if (biomeEnd > colonPos) {
                                    String biomeInfo = content.substring(colonPos + 1, Math.min(biomeEnd, colonPos + 100));
                                    biomeInfo = biomeInfo.replace("\n", " ").replace("\"", "").replace("[", "").replace("]", "").trim();
                                    info.biomeTag = biomeInfo;
                                }
                            }
                        }

                        info.foundInStructureFile = true;

                        // Add to result
                        StructureInfo structureInfo = result.structures.computeIfAbsent(structureId, StructureInfo::new);
                        structureInfo.jarWeight = info.weight;
                        structureInfo.jarSet = info.structureSet != null ? info.structureSet : "NONE (uncategorized)";
                        structureInfo.jarBiome = info.biomeTag;
                        structureInfo.jarSource = info.foundInStructureSet ? "structure_set" : "structure_file";
                    } catch (Exception e) {
                        MVSCommon.LOGGER.debug("Failed to parse structure in datapack directory: {}", e.getMessage());
                    }
                });
        }
    }
}

// ============================================================
// PHASE 2: MERGE METHOD
// ============================================================

/**
 * Merge all collected data into unified registry.
 * Returns new ScanResult with merged data (no mutation of input).
 */
private static ScanResult mergeRegistryData(
        ScanResult result,
        Map<String, StructureInfo> registryStructures,
        JarScanData jarData) {

    // Merge registry structures
    for (Map.Entry<String, StructureInfo> entry : registryStructures.entrySet()) {
        result.allStructures.put(entry.getKey(), entry.getValue());
    }

    // Merge JAR structures
    for (Map.Entry<String, StructureInfo> entry : jarData.structures.entrySet()) {
        StructureInfo existing = result.allStructures.get(entry.getKey());
        if (existing != null) {
            // Merge JAR data into existing structure
            StructureInfo jarInfo = entry.getValue();
            existing.jarWeight = jarInfo.jarWeight;
            existing.jarSet = jarInfo.jarSet;
            existing.jarBiome = jarInfo.jarBiome;
            existing.jarSource = jarInfo.jarSource;
        } else {
            // New structure from JAR
            result.allStructures.put(entry.getKey(), entry.getValue());
        }
    }

    // Merge structure set info
    result.structureSetInfo.putAll(jarData.structureSetInfo);

    return result;
}

// ============================================================
// PHASE 3: FILTER METHOD
// ============================================================

/**
 * Filter unified registry for village-related structures only.
 * Returns structures that:
 * - Are in *:villages structure_sets, OR
 * - Have "village" or "town" in their name, OR
 * - Are uncategorized but in overworld biomes
 */
private static Map<String, StructureInfo> filterVillageStructures(
        Map<String, StructureInfo> allStructures) {

    Map<String, StructureInfo> villageStructures = new HashMap<>();

    for (StructureInfo info : allStructures.values()) {
        // Filter 1: Skip dimension-specific structures (aether, end, nether, deep_dark)
        String biomeTag = info.getFinalBiome();
        if (isDimensionSpecific(info.id, biomeTag)) {
            continue;
        }

        String setId = info.getFinalSet();
        String nameLower = info.id.toLowerCase();

        // Include if:
        // - In a *:villages structure_set
        // - Has "village" or "town" in name
        // - Uncategorized (might be village-related)
        if ((setId != null && setId.endsWith(":villages")) ||
            nameLower.contains("village") ||
            nameLower.contains("town") ||
            setId.equals("NONE (uncategorized)")) {

            villageStructures.put(info.id, info);
        }
    }

    return villageStructures;
}

// ============================================================
// PHASE 4: CATEGORIZE METHOD
// ============================================================

/**
 * Categorize filtered structures into suggestion sets:
 * - Core: *:villages structure_sets
 * - Likely: Custom sets that appear to be villages
 * - Questionable: Ambiguous structures
 * Returns new ScanResult with categorized structures.
 */
private static ScanResult categorizeStructures(
        ScanResult result,
        Map<String, StructureInfo> villageStructures) {

    for (StructureInfo info : villageStructures.values()) {
        String setId = info.getFinalSet();

        if (setId != null && setId.endsWith(":villages")) {
            // Core village structures (*:villages sets)
            result.coreStructures.add(info);
        } else if (setId != null && !setId.equals("NONE (uncategorized)")) {
            // Has custom structure_set - check if it's a village
            if (!info.id.toLowerCase().contains("village")) {
                continue; // Not a village structure
            }

            // Check UNLIKELY patterns
            if (isUnlikelyByName(info.id)) {
                continue; // Skip unlikely structures
            }

            // LIKELY vs QUESTIONABLE
            if (isLikelyVillageStructureSet(setId, villageStructures)) {
                result.likelyStructures.add(info);
            } else {
                result.questionableStructures.add(info);
            }
        }
    }

    return result;
}

// ============================================================
// HELPER METHODS
// ============================================================

/**
 * Check if structure_set is likely a village (vs questionable)
 */
public static boolean isLikelyVillageStructureSet(String setId, Map<String, StructureInfo> allStructures) {
    String setName = setId.toLowerCase();

    // Pattern 1: Structure_set name contains "village" or "town"
    if (setName.contains("village") || setName.contains("town")) {
        return true;
    }

    // Pattern 2: Multiple structures in this structure_set
    long structureCountInSet = allStructures.values().stream()
        .filter(info -> setId.equals(info.getFinalSet()))
        .count();

    return structureCountInSet > 1;
}

/**
 * Check if structure name has UNLIKELY patterns (decorations, not villages)
 */
public static boolean isUnlikelyByName(String structureName) {
    String lower = structureName.toLowerCase();

    // Check if structure_set name exactly matches structure name (underwater_village pattern)
    // We can't check this here without StructureJarInfo, so skip for now

    // Structure name contains non-village keywords
    String[] nonVillageKeywords = {
        "statue", "monument", "decoration", "fountain", "well",
        "warship", "ship", "gate", "tower", "shrine", "temple",
        "library", "dungeon", "ruins", "broken"
    };

    for (String keyword : nonVillageKeywords) {
        if (lower.contains(keyword)) {
            return true;
        }
    }

    return false;
}

/**
 * Calculate normalized weights for a set of structures
 * Returns map of original_weight -> normalized_weight
 *
 * NOTE: For minecraft:villages structure_set, we always use VANILLA_SPACING (34) for
 * rarity calculations regardless of actual spacing. This is because mod authors design
 * their weights assuming vanilla 34/8 spacing - datapacks that modify spacing shouldn't
 * affect weight normalization.
 */
public static Map<Integer, Integer> calculateNormalizedWeights(
        List<StructureInfo> setStructures,
        StructureSetInfo setInfo) {

    // Calculate original total and weight distribution
    int originalTotal = 0;
    Map<Integer, Integer> weightCounts = new HashMap<>();
    for (StructureInfo info : setStructures) {
        Integer weight = info.getFinalWeight();
        if (weight != null) {
            originalTotal += weight;
            weightCounts.put(weight, weightCounts.getOrDefault(weight, 0) + 1);
        }
    }

    Map<Integer, Integer> result = new HashMap<>();
    if (originalTotal == 0 || setStructures.isEmpty()) {
        return result;
    }

    double originalAverage = originalTotal / (double) setStructures.size();

    // Calculate rarity factor based on spacing
    // EXCEPTION: For minecraft:villages, always assume VANILLA_SPACING because:
    // 1. Mod authors design weights assuming vanilla 34/8 spacing
    // 2. Datapacks may modify spacing (e.g., 6/5 for testing) which shouldn't affect normalization
    double rarityFactor = 1.0;
    Integer spacing = (setInfo != null) ? setInfo.spacing : null;
    String setId = (setInfo != null) ? setInfo.setId : null;
    boolean isVanillaVillages = MINECRAFT_VILLAGES_SET.equals(setId);

    if (setInfo != null && setInfo.spacing != null && setInfo.spacing > 0 && !isVanillaVillages) {
        double spacingRatio = setInfo.spacing / (double) VANILLA_SPACING;
        rarityFactor = spacingRatio * spacingRatio;  // Square for area effect

        // Cap rarity factor at 10.0 to prevent ultra-rare structures from getting penalized
        // (spacing > ~106 chunks would exceed this cap)
        if (rarityFactor > 10.0) {
            rarityFactor = 10.0;
        }
    }

    double multiplier = (TARGET_AVERAGE_WEIGHT / originalAverage) / rarityFactor;

    MVSCommon.LOGGER.debug("Weight normalization: spacing={}, rarityFactor={}, multiplier={}",
        spacing, String.format("%.2f", rarityFactor), String.format("%.2f", multiplier));

    // Calculate normalized weights
    for (Integer originalWeight : weightCounts.keySet()) {
        int normalized = (int) Math.ceil(originalWeight * multiplier);
        result.put(originalWeight, normalized);
    }

    return result;
}

/**
 * Add weight analysis section for a structure_set
 * Shows normalization with average-based strategy (target avg: 25)
 * Applies rarity adjustment based on spacing compared to vanilla villages
 */
public static void addWeightAnalysisForSet(
        List<String> lines,
        List<StructureInfo> setStructures,
        StructureSetInfo setInfo) {

    int emptyWeight = setInfo != null && setInfo.emptyWeight != null ? setInfo.emptyWeight : 0;

    if (setStructures.isEmpty()) return;

    // Calculate original total and average
    int originalTotal = 0;
    Map<Integer, Integer> weightCounts = new HashMap<>();  // Track weight distribution
    Map<Integer, List<String>> weightToStructures = new HashMap<>();  // Track which structures have which weight

    for (StructureInfo info : setStructures) {
        Integer weight = info.getFinalWeight();
        if (weight != null) {
            originalTotal += weight;
            weightCounts.put(weight, weightCounts.getOrDefault(weight, 0) + 1);

            // Extract simple structure name (last part after /)
            String simpleName = info.id.contains("/") ?
                info.id.substring(info.id.lastIndexOf("/") + 1) :
                info.id.substring(info.id.lastIndexOf(":") + 1);
            weightToStructures.computeIfAbsent(weight, k -> new ArrayList<>()).add(simpleName);
        }
    }

    if (originalTotal == 0) return;

    double originalAverage = originalTotal / (double) setStructures.size();

    // Calculate rarity factor based on spacing (for display purposes)
    // EXCEPTION: For minecraft:villages, skip rarity adjustment (same as calculateNormalizedWeights)
    double rarityFactor = 1.0;
    String setId = (setInfo != null) ? setInfo.setId : null;
    boolean isVanillaVillages = MINECRAFT_VILLAGES_SET.equals(setId);

    if (setInfo != null && setInfo.spacing != null && setInfo.spacing > 0 && !isVanillaVillages) {
        double spacingRatio = setInfo.spacing / (double) VANILLA_SPACING;
        rarityFactor = spacingRatio * spacingRatio;  // Square for area effect
    }

    double multiplier = rarityFactor != 1.0 ?
        (TARGET_AVERAGE_WEIGHT / originalAverage) / rarityFactor :
        (TARGET_AVERAGE_WEIGHT / originalAverage);

    // Use shared normalization helper
    Map<Integer, Integer> originalToNormalized = calculateNormalizedWeights(setStructures, setInfo);

    // Calculate normalized total
    int normalizedTotal = 0;
    for (Integer originalWeight : weightCounts.keySet()) {
        Integer normalized = originalToNormalized.get(originalWeight);
        if (normalized != null) {
            normalizedTotal += normalized * weightCounts.get(originalWeight);
        }
    }

    // Detect pattern
    boolean isUniform = weightCounts.size() == 1;
    String pattern;
    String ratio = "";
    if (isUniform) {
        pattern = "Even distribution (all weight=" + weightCounts.keySet().iterator().next() + ")";
    } else {
        // Show ratio
        List<Integer> uniqueWeights = new ArrayList<>(weightCounts.keySet());
        uniqueWeights.sort(Comparator.reverseOrder());
        ratio = uniqueWeights.stream().map(String::valueOf).reduce((a, b) -> a + ":" + b).orElse("");

        // Show normalized ratio
        List<Integer> normalizedWeights = new ArrayList<>();
        for (Integer w : uniqueWeights) {
            normalizedWeights.add(originalToNormalized.get(w));
        }
        String normalizedRatio = normalizedWeights.stream().map(String::valueOf).reduce((a, b) -> a + ":" + b).orElse("");

        pattern = String.format("Intentional variance (ratio %s preserved as %s)", ratio, normalizedRatio);
    }

    // Output analysis
    lines.add("WEIGHT ANALYSIS:");
    lines.add(String.format("  Original Total: %d (%d structures)", originalTotal, setStructures.size()));
    lines.add(String.format("  Original Average: %.1f", originalAverage));

    // Show spacing and rarity if available
    if (setInfo != null && setInfo.spacing != null && setInfo.spacing > 0) {
        lines.add(String.format("  Spacing: %d chunks (separation: %d) - vanilla: %d chunks",
            setInfo.spacing,
            setInfo.separation != null ? setInfo.separation : 0,
            VANILLA_SPACING));
        if (rarityFactor != 1.0) {
            lines.add(String.format("  Rarity: %.1fx compared to vanilla (spacing-based adjustment)",
                rarityFactor));
        }
    }

    lines.add(String.format("  Normalized Total: %d (target avg: %d, multiplier: %.3f)",
        normalizedTotal, TARGET_AVERAGE_WEIGHT, multiplier));
    lines.add("");

    // Spawn probability with empty weight
    int grandTotal = originalTotal + emptyWeight;
    double spawnChance = (originalTotal * 100.0) / grandTotal;
    double emptyChance = (emptyWeight * 100.0) / grandTotal;
    lines.add("  Spawn Probability (with empty weight):");
    lines.add(String.format("    Total: %d (%d structures + %d empty)", grandTotal, originalTotal, emptyWeight));
    lines.add(String.format("    Spawn Chance: %.1f%% (%d/%d)", spawnChance, originalTotal, grandTotal));
    lines.add(String.format("    Empty Chance: %.1f%% (%d/%d)", emptyChance, emptyWeight, grandTotal));
    lines.add("");

    // Weight distribution
    lines.add(String.format("  Weight Distribution (original → normalized to avg %d):", TARGET_AVERAGE_WEIGHT));

    // Sort weights descending
    List<Integer> sortedWeights = new ArrayList<>(weightCounts.keySet());
    sortedWeights.sort(Comparator.reverseOrder());

    for (Integer originalWeight : sortedWeights) {
        int count = weightCounts.get(originalWeight);
        int normalized = originalToNormalized.get(originalWeight);
        double percent = (originalWeight * count * 100.0) / originalTotal;

        // Get structure names
        List<String> structNames = weightToStructures.get(originalWeight);
        String structList = String.join(", ", structNames.size() > 3 ?
            structNames.subList(0, 3) : structNames);
        if (structNames.size() > 3) structList += ", ...";

        lines.add(String.format("    %2d → %2d  (%.1f%%) [%d structure%s: %s]",
            originalWeight, normalized, percent, count, count == 1 ? "" : "s", structList));
    }
    lines.add("");

    lines.add("  Pattern: " + pattern);
    lines.add("  Strategy: Normalize by average weight (target: " + TARGET_AVERAGE_WEIGHT + ")");
    lines.add("");
}

/**
 * Helper method to filter out dimension-specific structures
 * Filters: aether, end, nether, deep_dark dimensions
 */
public static boolean isDimensionSpecific(String structureId, String biomeTag) {
    String combined = (structureId + " " + (biomeTag != null ? biomeTag : "")).toLowerCase();
    return combined.contains("aether") ||
           combined.contains("_end") ||
           combined.contains("end_") ||
           combined.contains("ender") ||
           combined.contains("nether") ||
           combined.contains("deep_dark");
}

/**
 * Scan loaded mod JARs for ALL structure definitions
 */
public static Map<String, List<StructureJarInfo>> scanModJarsForAllStructures(net.minecraft.server.MinecraftServer server) {
    Map<String, List<StructureJarInfo>> modStructures = new java.util.TreeMap<>();

    try {
        // Get all loaded mod containers via Architectury Platform API
        for (var mod : dev.architectury.platform.Platform.getMods()) {
            String modId = mod.getModId();
            var dataRoot = mod.findResource("data").orElse(null);

            try {
                // First, scan structure_set files to get weights (discard empty weights for this helper)
                Map<String, StructureJarInfo> structureSetData = scanAllStructureSetFiles(dataRoot, modId, new HashMap<>());

                // Then, scan individual structure files and merge with structure_set data
                List<StructureJarInfo> structures = scanAllStructureFiles(dataRoot, modId, structureSetData);

                if (!structures.isEmpty()) {
                    modStructures.put(modId, structures);
                }

            } catch (Exception e) {
                MVSCommon.LOGGER.warn("Failed to scan mod {}: {}", modId, e.getMessage());
            }
        }

        // ALSO scan .connector directory for Fabric mods loaded via Sinytra Connector
        scanConnectorDatapacks(server, modStructures);

    } catch (Exception e) {
        MVSCommon.LOGGER.error("Error scanning mod JARs", e);
    }

    return modStructures;
}

/**
 * Scan .connector/data directory for Fabric mods loaded via Sinytra Connector
 * BCA (Cobblemon Additions) extracts its datapack here at runtime
 */
public static void scanConnectorDatapacks(net.minecraft.server.MinecraftServer server, Map<String, List<StructureJarInfo>> modStructures) {
    try {
        Path gameDir = server.getServerDirectory();
        Path connectorDir = gameDir.resolve("mods/.connector/data");

        if (!java.nio.file.Files.exists(connectorDir)) {
            return;
        }

        // Scan structure_set files first
        Map<String, StructureJarInfo> connectorStructureSets = new HashMap<>();
        java.nio.file.Files.walk(connectorDir)
            .filter(path -> path.toString().contains("worldgen/structure_set"))
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = connectorDir.relativize(path).toString();
                    String[] parts = relativePath.split("/");

                    if (parts.length >= 4) {
                        String namespace = parts[0];
                        String setName = path.getFileName().toString().replace(".json", "");
                        String setId = namespace + ":" + setName;

                        // Parse JSON using Gson
                        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                        if (root.has("structures")) {
                            JsonArray structures = root.getAsJsonArray("structures");

                            for (JsonElement element : structures) {
                                JsonObject structureEntry = element.getAsJsonObject();

                                if (structureEntry.has("structure")) {
                                    String structureId = structureEntry.get("structure").getAsString();
                                    Integer weight = structureEntry.has("weight") ?
                                        structureEntry.get("weight").getAsInt() : null;

                                    // Store - figure out mod from structure ID
                                    String modId = structureId.contains(":") ? structureId.split(":")[0] : "unknown";
                                    StructureJarInfo info = connectorStructureSets.computeIfAbsent(structureId,
                                        k -> new StructureJarInfo(k, modId));
                                    info.structureSet = setId;
                                    info.weight = weight;
                                    info.foundInStructureSet = true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    MVSCommon.LOGGER.warn("[JAR-SCAN] Failed to parse connector structure_set: {}", e.getMessage());
                }
            });

        // Now scan structure files and merge
        java.nio.file.Files.walk(connectorDir)
            .filter(path -> path.toString().contains("worldgen/structure/"))
            .filter(path -> !path.toString().contains("structure_set"))
            .filter(path -> !path.toString().contains("/tags/"))
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = connectorDir.relativize(path).toString();

                    String[] parts = relativePath.split("/");
                    if (parts.length >= 4) {
                        String namespace = parts[0];
                        StringBuilder structurePathBuilder = new StringBuilder();
                        for (int i = 3; i < parts.length; i++) {
                            if (i > 3) structurePathBuilder.append("/");
                            structurePathBuilder.append(parts[i]);
                        }
                        String structurePath = structurePathBuilder.toString().replace(".json", "");
                        String structureId = namespace + ":" + structurePath;

                        // Get or create info
                        StructureJarInfo info = connectorStructureSets.get(structureId);
                        if (info == null) {
                            String modId = namespace;
                            info = new StructureJarInfo(structureId, modId);
                        }

                        // Extract biome tag
                        if (content.contains("\"biomes\"")) {
                            int biomeStart = content.indexOf("\"biomes\"");
                            int colonPos = content.indexOf(":", biomeStart);
                            if (colonPos > biomeStart) {
                                int biomeEnd = content.indexOf(",", colonPos);
                                if (biomeEnd == -1) biomeEnd = content.indexOf("}", colonPos);
                                if (biomeEnd > colonPos) {
                                    String biomeInfo = content.substring(colonPos + 1, Math.min(biomeEnd, colonPos + 100));
                                    biomeInfo = biomeInfo.replace("\n", " ").replace("\"", "").replace("[", "").replace("]", "").trim();
                                    info.biomeTag = biomeInfo;
                                }
                            }
                        }

                        info.foundInStructureFile = true;

                        // Add to appropriate mod list
                        modStructures.computeIfAbsent(info.modId, k -> new ArrayList<>()).add(info);
                    }
                } catch (Exception e) {
                    // Skip
                }
            });

    } catch (Exception e) {
        MVSCommon.LOGGER.warn("[JAR-SCAN] Error scanning .connector directory: {}", e.getMessage());
    }
}

/**
 * Scan a mod file for ALL structure_set definitions and extract weights using Gson
 * Returns map of structure_id -> StructureJarInfo with weight and set info
 * Also tracks empty weights in structureSetInfo map
 */
public static Map<String, StructureJarInfo> scanAllStructureSetFiles(
        Path dataRoot,
        String modId,
        Map<String, StructureSetInfo> structureSetInfo) {
    Map<String, StructureJarInfo> structureSetData = new HashMap<>();
    final String finalModId = modId;

    try {
        if (dataRoot == null || !java.nio.file.Files.exists(dataRoot)) {
            return structureSetData;
        }

        // Walk through data/*/worldgen/structure_set/*.json
        java.nio.file.Files.walk(dataRoot)
            .filter(path -> path.toString().contains("worldgen/structure_set"))
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = dataRoot.relativize(path).toString();

                    String[] parts = relativePath.split("/");
                    if (parts.length >= 4) {
                        String namespace = parts[0];
                        String setName = path.getFileName().toString().replace(".json", "");
                        String setId = namespace + ":" + setName;

                        // Parse JSON using Gson
                        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
                        if (root.has("structures")) {
                            JsonArray structures = root.getAsJsonArray("structures");

                            for (JsonElement element : structures) {
                                JsonObject structureEntry = element.getAsJsonObject();

                                if (structureEntry.has("structure")) {
                                    String structureId = structureEntry.get("structure").getAsString();
                                    Integer weight = structureEntry.has("weight") ?
                                        structureEntry.get("weight").getAsInt() : null;

                                    // Store structure_set info
                                    StructureJarInfo info = structureSetData.computeIfAbsent(structureId,
                                        k -> new StructureJarInfo(k, finalModId));
                                    info.structureSet = setId;
                                    info.weight = weight;
                                    info.foundInStructureSet = true;
                                } else if (structureEntry.has("weight")) {
                                    // Empty entry! Track empty weight for this structure_set
                                    Integer emptyWeight = structureEntry.get("weight").getAsInt();
                                    StructureSetInfo setInfo = structureSetInfo.computeIfAbsent(setId,
                                        k -> new StructureSetInfo(k));
                                    setInfo.emptyWeight = emptyWeight;
                                }
                            }
                        }

                        // Extract spacing/separation from placement
                        if (root.has("placement")) {
                            JsonObject placement = root.getAsJsonObject("placement");
                            StructureSetInfo setInfo = structureSetInfo.computeIfAbsent(setId,
                                k -> new StructureSetInfo(k));

                            if (placement.has("spacing")) {
                                setInfo.spacing = placement.get("spacing").getAsInt();
                            }
                            if (placement.has("separation")) {
                                setInfo.separation = placement.get("separation").getAsInt();
                            }
                        }
                    }

                } catch (Exception e) {
                    // Skip files that can't be parsed - this is expected for some JSON files
                    MVSCommon.LOGGER.debug("Failed to parse structure_set file: {}", e.getMessage());
                }
            });

    } catch (Exception e) {
        // Skip if can't access mod data directory - this is expected for some mods
        MVSCommon.LOGGER.debug("Failed to access mod data directory: {}", e.getMessage());
    }

    return structureSetData;
}

/**
 * Scan a mod file for ALL individual structure definitions
 * Merges with structure_set data if available
 */
public static List<StructureJarInfo> scanAllStructureFiles(
    Path dataRoot,
    String modId,
    Map<String, StructureJarInfo> structureSetData
) {
    List<StructureJarInfo> structures = new ArrayList<>();

    try {
        if (dataRoot == null || !java.nio.file.Files.exists(dataRoot)) {
            return structures;
        }

        // Walk through data/*/worldgen/structure/*.json (NOT structure_set, NOT tags)
        java.nio.file.Files.walk(dataRoot)
            .filter(path -> path.toString().contains("worldgen/structure/"))
            .filter(path -> !path.toString().contains("structure_set"))
            .filter(path -> !path.toString().contains("/tags/"))  // Exclude tag files
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(path -> {
                try {
                    String content = java.nio.file.Files.readString(path);
                    String relativePath = dataRoot.relativize(path).toString();

                    // Extract namespace and structure name from path
                    // dataRoot is already at "data/", so relativePath is like: bca/worldgen/structure/village/default_small.json
                    String[] parts = relativePath.split("/");
                    if (parts.length >= 4) {  // Need at least namespace/worldgen/structure/file.json
                        String namespace = parts[0];  // First part is the namespace (no "data/" prefix)

                        // Build structure path from parts (handle nested directories)
                        // e.g. village/default_small or just village_plains
                        // Start from parts[3] to skip namespace/worldgen/structure
                        StringBuilder structurePathBuilder = new StringBuilder();
                        for (int i = 3; i < parts.length; i++) {
                            if (i > 3) structurePathBuilder.append("/");
                            structurePathBuilder.append(parts[i]);
                        }
                        String structurePath = structurePathBuilder.toString().replace(".json", "");

                        // Full structure ID
                        String structureId = namespace + ":" + structurePath;

                        // Check if we already have structure_set data for this structure
                        StructureJarInfo info = structureSetData.get(structureId);
                        if (info == null) {
                            info = new StructureJarInfo(structureId, modId);
                        }

                        // Extract biome tag from JSON
                        if (content.contains("\"biomes\"")) {
                            int biomeStart = content.indexOf("\"biomes\"");
                            int colonPos = content.indexOf(":", biomeStart);
                            if (colonPos > biomeStart) {
                                int biomeEnd = content.indexOf(",", colonPos);
                                if (biomeEnd == -1) biomeEnd = content.indexOf("}", colonPos);
                                if (biomeEnd > colonPos) {
                                    String biomeInfo = content.substring(colonPos + 1, Math.min(biomeEnd, colonPos + 100));
                                    biomeInfo = biomeInfo.replace("\n", " ").replace("\"", "").replace("[", "").replace("]", "").trim();
                                    info.biomeTag = biomeInfo;
                                }
                            }
                        }

                        info.foundInStructureFile = true;
                        structures.add(info);
                    }

                } catch (Exception e) {
                    // Skip files that can't be parsed - this is expected for some JSON files
                    MVSCommon.LOGGER.debug("Failed to parse structure file: {}", e.getMessage());
                }
            });

    } catch (Exception e) {
        // Skip if can't access mod data directory - this is expected for some mods
        MVSCommon.LOGGER.debug("Failed to access mod data directory: {}", e.getMessage());
    }

    return structures;
}
}

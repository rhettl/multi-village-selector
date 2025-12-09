# Getting Started with Multi Village Selector

This guide walks you through installing MVS and getting your first config working.

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [First Launch](#first-launch)
- [Generate Your Config](#generate-your-config)
- [Configure Other Mods](#configure-other-mods)
- [Next Steps](#next-steps)
- [Common First-Time Issues](#common-first-time-issues)

## Requirements

- **Minecraft** 1.21.1
- **Mod Loader**: NeoForge 21.1.80+ OR Fabric Loader 0.16.0+
- **Architectury API**: Required for both platforms

**Recommended village mods** (install any you like):
- Cobblemon Additions (BCA)
- CTOV (ChoiceTheorem's Overhauled Village)
- Towns & Towers
- Terralith

(No reason for these specific mods, I just like 'em)

## Installation

### NeoForge

1. Download from [Modrinth](https://modrinth.com/mod/multi-village-selector) or [GitHub Releases](https://github.com/RhettL/multi-village-selector/releases):
   - `multivillageselector-neoforge-<version>.jar`
   - `architectury-api` (NeoForge version)

2. Place both JARs in your `mods/` folder

### Fabric

1. Download from [Modrinth](https://modrinth.com/mod/multi-village-selector) or [GitHub Releases](https://github.com/RhettL/multi-village-selector/releases):
   - `multivillageselector-fabric-<version>.jar`
   - `architectury-api` (Fabric version)
   - `fabric-api` (if not already installed)          

2. Place all JARs in your `mods/` folder

## First Launch

1. **Launch Minecraft** with your chosen mod loader
2. **Create a new world** or load an existing one
3. MVS creates a default config at `config/multivillageselector.json5`

The default config behaves just like vanilla. Run `/mvs generate` to create a config tailored to your installed mods.

```json5
{
  enabled: true,

  // MVS takes control of these structure_sets
  intercept_structure_sets: [ "minecraft:villages" ],

  // Structures MVS can spawn
  structure_pool: [
    { structure: "minecraft:village_plains",
      biomes: { "#minecraft:has_structure/village_plains": 25 } },
    { structure: "minecraft:village_desert",
      biomes: { "#minecraft:has_structure/village_desert": 25 } },
    { structure: "minecraft:village_savanna",
      biomes: { "#minecraft:has_structure/village_savanna": 25 } },
    { structure: "minecraft:village_taiga",
      biomes: { "#minecraft:has_structure/village_taiga": 25 } },
    { structure: "minecraft:village_snowy",
      biomes: { "#minecraft:has_structure/village_snowy": 25 } },
  ],
}
```

## Generate Your Config

> **Note:** MVS commands require operator permissions. In single player, enable cheats when creating the world or open to LAN with cheats enabled.

In-game, the `/mvs generate` command scans all your installed mods and creates a complete config base:

1. **In-game**, open chat and run:
   ```
   /mvs generate
   ```

2. MVS scans your mods and outputs a new config to:
   ```
   <minecraft-instance>/local/mvs/multivillageselector.json5
   ```

3. **Review the generated file** - it contains all detected village structures with normalized weights. It makes some **guesses** and may miss some structures or be wrong about some weights -- **Please review before using.**

4. **Move it to your config folder** - located at `<minecraft-instance>/config`

5. **Restart Minecraft** to apply the new config

6. **Verify it's working** - Run `/mvs info` to see your pool size and intercepted structure sets

### What `/mvs generate` Does

- Scans all loaded mods for village structures
- Detects structure_sets that contain villages
- Normalizes weights so each mod is comparable, roughly
- Preserves each mod's internal weight ratios (as much as possible)
- Generates biome rules from vanilla registry data

## Configure Other Mods

Some village mods need their own spawning disabled so MVS can take full control. This varies by mod - some work automatically, others need config changes, and a few override spacing settings at runtime.

For specific instructions per mod, see the [Mod Compatibility Guide](ModCompatibility.md).

## Next Steps

Now that MVS is working:

1. **Customize weights** - See [Configuration Guide](Configuration.md) to adjust spawn rates
2. **Control density** - See [Spacing Guide](SpacingGuide.md) to change how often villages spawn
3. **Troubleshoot issues** - See [Troubleshooting](Troubleshooting.md) if something isn't working

**Keep in mind:** Spacing, frequency, weight, and biomes **all** play a part in village selection and density.

## Common First-Time Issues

### "No villages are spawning"

- Check that `intercept_structure_sets` includes `"minecraft:villages"`
- Verify `structure_pool` isn't empty
- Make sure you restarted after changing config

### "Only vanilla villages spawn"

- Run `/mvs generate` to detect installed mods
- Copy generated config to `config/` folder
- Disable other mods' village spawning (see [Mod Compatibility](ModCompatibility.md))

### "Config syntax error on startup"

- JSON5 allows comments and trailing commas, but check for typos
- Validate with a JSON5 linter
- See [Troubleshooting](Troubleshooting.md) for common syntax issues

---

**Next:** [Configuration Guide](Configuration.md) | [Mod Compatibility](ModCompatibility.md)

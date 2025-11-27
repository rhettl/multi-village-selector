# Getting Started with Multi Village Selector

This guide walks you through installing MVS and getting your first config working.

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [First Launch](#first-launch)
- [Generate Your Config](#generate-your-config)
- [Configure Other Mods](#configure-other-mods)
- [Verify It's Working](#verify-its-working)
- [Next Steps](#next-steps)

## Requirements

- **Minecraft** 1.21.1
- **Mod Loader**: NeoForge 21.1.80+ OR Fabric Loader 0.16.0+
- **Architectury API**: Required for both platforms

**Recommended village mods** (install any you like):
- CTOV (ChoiceTheorem's Overhauled Village)
- Towns & Towers
- Cobblemon Additions (BCA)
- Terralith
- Better Villages

## Installation

### NeoForge

1. Download from [Modrinth](<!-- TODO: Add link -->) or [GitHub Releases](https://github.com/RhettL/multi-village-selector/releases):
   - `multivillageselector-neoforge-0.3.0.jar`
   - `architectury-api` (NeoForge version)

2. Place both JARs in your `mods/` folder

### Fabric

1. Download from [Modrinth](<!-- TODO: Add link -->) or [GitHub Releases](https://github.com/RhettL/multi-village-selector/releases):
   - `multivillageselector-fabric-0.3.0.jar`
   - `architectury-api` (Fabric version)
   - `fabric-api` (if not already installed)

2. Place all JARs in your `mods/` folder

## First Launch

1. **Launch Minecraft** with your chosen mod loader
2. **Create a new world** or load an existing one
3. MVS creates a default config at `config/multivillageselector.json5`

The default config is minimal:

```json5
{
  enabled: true,
  debug_logging: false,
  intercept_structure_sets: [],  // Empty - MVS not active yet
  structure_pool: [],
}
```

## Generate Your Config

The `/mvs generate` command scans all your installed mods and creates a complete config:

1. **In-game**, open chat and run:
   ```
   /mvs generate
   ```

2. MVS scans your mods and outputs a new config to:
   ```
   local/mvs/multivillageselector.json5
   ```

3. **Review the generated file** - it contains all detected village structures with normalized weights

4. **Copy to your config folder**:
   ```
   cp local/mvs/multivillageselector.json5 config/multivillageselector.json5
   ```

5. **Restart Minecraft** to apply the new config

### What `/mvs generate` Does

- Scans all loaded mods for village structures
- Detects structure_sets that contain villages
- Normalizes weights so each mod gets equal representation
- Preserves each mod's internal weight ratios
- Generates biome rules from vanilla registry data

## Configure Other Mods

Most village mods need their own spawning disabled so MVS can control everything.

### CTOV (ChoiceTheorem's Overhauled Village)

Edit `config/ctov-common.toml`:

```toml
[structures]
    generatesmallVillage = false
    generatemediumVillage = false
    generatelargeVillage = false
```

### Better Village

Edit `config/bettervillage_1.properties`:

```properties
# CRITICAL: Better Village overrides spacing settings at runtime
# Must disable to let MVS control village density
boolean.villages.enabled_custom_config=false
```

### Cobblemon Additions (BCA)

BCA uses a datapack override - MVS handles this automatically when you run `/mvs generate` with BCA installed. No manual config needed.

### Other Mods

See [Mod Compatibility](ModCompatibility.md) for complete per-mod instructions.

## Verify It's Working

### Check Current Biome

Stand in any biome and run:

```
/mvs biome
```

Output shows:
- Your coordinates
- Current biome ID
- Biome tags
- Which structures can spawn here

### Check Structure Rules

For any structure, run:

```
/mvs structure biomes minecraft:village_plains
```

Output shows:
- Which biomes this structure can spawn in
- Weight in each biome
- Source (MVS config or vanilla registry)

### Enable Debug Logging

For detailed spawn information, edit your config:

```json5
{
  debug_logging: true,
  // ... rest of config
}
```

Then check `logs/latest.log` for entries like:

```
[MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
[MVS] Generation FAILED: minecraft:village_desert - biome mismatch
```

## Next Steps

Now that MVS is working:

1. **Customize weights** - See [Configuration Guide](Configuration.md) to adjust spawn rates
2. **Control density** - See [Spacing Guide](SpacingGuide.md) to change how often villages spawn
3. **Troubleshoot issues** - See [Troubleshooting](Troubleshooting.md) if something isn't working

## Common First-Time Issues

### "No villages are spawning"

- Check that `intercept_structure_sets` includes `"minecraft:villages"`
- Verify `structure_pool` isn't empty
- Make sure you restarted after changing config

### "Only vanilla villages spawn"

- Run `/mvs generate` to detect installed mods
- Copy generated config to `config/` folder
- Disable other mods' village spawning (see above)

### "Config syntax error on startup"

- JSON5 allows comments and trailing commas, but check for typos
- Validate with a JSON5 linter
- See [Troubleshooting](Troubleshooting.md) for common syntax issues

---

**Next:** [Configuration Guide](Configuration.md) | [Mod Compatibility](ModCompatibility.md)

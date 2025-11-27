# Mod Compatibility Guide

This guide explains how to configure MVS to work with popular village mods.

## Table of Contents

- [Quick Reference](#quick-reference)
- [CTOV](#ctov-choicetheorems-overhauled-village)
- [Better Village](#better-village)
- [BCA (Cobblemon Additions)](#bca-cobblemon-additions)
- [Towns & Towers](#towns--towers)
- [Terralith](#terralith)
- [Testing New Mods](#testing-new-mods)
- [Known Issues](#known-issues)

## Quick Reference

| Mod | Type | Disable Spawning? | Notes |
|-----|------|------------------|-------|
| CTOV | Structure adder | Yes (config) | Disable village generation in ctov-common.toml |
| Better Village | Jigsaw replacer | Disable spacing only | **Critical:** Disable `enabled_custom_config` |
| Luki's Grand Capitals | Jigsaw replacer | No | Works alongside MVS automatically |
| BCA | Structure adder | No | MVS intercepts automatically |
| Towns & Towers | Structure adder | No | Works out of the box |
| Terralith | Structure adder | No | Works out of the box |

## CTOV (ChoiceTheorem's Overhauled Village)

### Required: Disable CTOV Village Spawning

Edit `config/ctov-common.toml`:

```toml
[structures]
    generatesmallVillage = false
    generatemediumVillage = false
    generatelargeVillage = false
```

**Why:** CTOV adds villages to vanilla structure sets. Without disabling CTOV spawning, you get both CTOV's natural spawns AND MVS selections, resulting in double density.

### MVS Configuration

```json5
structure_pool: [
  // CTOV villages by biome
  { structure: "ctov:village_plains", biomes: {"#minecraft:is_plains": 10} },
  { structure: "ctov:village_desert", biomes: {"#minecraft:is_desert": 10} },
  { structure: "ctov:village_taiga", biomes: {"#minecraft:is_taiga": 10} },
  { structure: "ctov:village_savanna", biomes: {"#minecraft:is_savanna": 10} },
  { structure: "ctov:village_snowy", biomes: {"#minecraft:is_snowy": 10} },

  // CTOV large variants (lower weight for rarity)
  { structure: "ctov:large/village_plains", biomes: {"#minecraft:is_plains": 5} },
  { structure: "ctov:large/village_desert", biomes: {"#minecraft:is_desert": 5} },
]
```

Or use patterns:

```json5
structure_pool: [
  // All CTOV villages in their matching biomes
  { structure: "ctov:*village_plains*", biomes: {"#minecraft:is_plains": 10} },
  { structure: "ctov:*village_desert*", biomes: {"#minecraft:is_desert": 10} },
]
```

## Better Village

### How Better Village Works

Better Village is a **jigsaw piece replacer**, not a structure adder. It replaces individual buildings inside vanilla villages with enhanced versions. MVS cannot control which Better Village pieces are used - it operates at a different level.

**What this means:**
- MVS selects *which village type* spawns (plains, desert, etc.)
- Better Village then modifies *what buildings* appear in that village
- Both work together without conflict

### Critical: Disable Custom Spacing Config

**This is required!** Better Village overrides village spacing at runtime.

Edit `config/bettervillage_1.properties`:

```properties
# CRITICAL: Must be false for MVS to control village density
boolean.villages.enabled_custom_config=false
```

**Why:** Better Village modifies the `minecraft:villages` structure_set definition at runtime, overriding spacing/separation values. MVS intercepts structure selection, but spacing comes from the structure_set definition. Without disabling Better Village's config, your MVS spacing settings are ignored.

**Symptom:** Villages only spawn in unexpected locations or at wrong density.

### MVS Configuration

Better Village structures use vanilla names, so they're already included if you have vanilla villages in your pool:

```json5
structure_pool: [
  { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} },
  // Better Village automatically enhances these vanilla villages
]
```

**Note:** You don't need to add Better Village to your `structure_pool` - it enhances whatever vanilla villages MVS selects.

## BCA (Cobblemon Additions)

### No Configuration Needed

BCA completely replaces the vanilla village structure_set via datapack. MVS automatically intercepts BCA spawn attempts.

### MVS Configuration

```json5
structure_pool: [
  // BCA default villages
  { structure: "bca:village/default_small", biomes: {"*:*": 16} },
  { structure: "bca:village/default_mid", biomes: {"*:*": 11} },
  { structure: "bca:village/default_large", biomes: {"*:*": 2} },

  // BCA dark forest villages
  { structure: "bca:village/dark_small", biomes: {"#minecraft:is_dark_forest": 16} },
  { structure: "bca:village/dark_mid", biomes: {"#minecraft:is_dark_forest": 11} },
]
```

**Note:** BCA structure names include `village/` in the path. The pattern `bca:default_*` won't match - use `bca:village/default_*`.

### BCA Intended Weights

From BCA's structure_set, these are the mod's spawn rates:

| Structure | Weight | Frequency |
|-----------|--------|-----------|
| default_small | 16 | Common |
| default_mid | 11 | Frequent |
| default_large | 2 | Rare |
| dark_small | 16 | Common |
| dark_mid | 11 | Frequent |

### Known Issue: Chunk Palette Corruption

BCA's Pokecenter structure can occasionally cause chunk generation crashes due to how Cobblemon's PC block is stored. This is a BCA issue, not MVS. The BCA dev is aware.

## Towns & Towers

### No Configuration Needed

Towns & Towers works out of the box with MVS.

### MVS Configuration

```json5
structure_pool: [
  // Towns & Towers villages
  { structure: "towns_and_towers:village_plains", biomes: {"#minecraft:is_plains": 10} },
  { structure: "towns_and_towers:village_desert", biomes: {"#minecraft:is_desert": 10} },
  { structure: "towns_and_towers:village_taiga", biomes: {"#minecraft:is_taiga": 10} },
  { structure: "towns_and_towers:village_savanna", biomes: {"#minecraft:is_savanna": 10} },
  { structure: "towns_and_towers:village_snowy", biomes: {"#minecraft:is_snowy": 10} },

  // Exclusive variants
  { structure: "towns_and_towers:exclusives/village_classic", biomes: {"#minecraft:is_plains": 8} },
  { structure: "towns_and_towers:exclusives/village_rustic", biomes: {"#minecraft:is_forest": 8} },
]
```

## Terralith

### No Configuration Needed

Terralith's fortified villages work with MVS automatically.

### MVS Configuration

```json5
structure_pool: [
  // Terralith fortified villages (rare)
  { structure: "terralith:fortified_village", biomes: {"#minecraft:is_plains": 5} },
  { structure: "terralith:fortified_desert_village", biomes: {"#minecraft:is_desert": 5} },
]
```

Lower weights preserve their intended rarity.

## Testing New Mods

To check if a new village mod works with MVS:

1. **Enable debug logging:**
   ```json5
   debug_logging: true
   ```

2. **Create a new world** and explore

3. **Check logs** for structure attempts:
   ```
   [MVS] Generation SUCCEEDED: modname:village_type at chunk [12, -5]
   ```

4. **Add structures** to your `structure_pool` with appropriate biome rules

5. **Disable the mod's spawning** if it has a config option (prevents double density)

## Known Issues

### CTOV + BCA Together

When using both:
1. Disable CTOV village spawning (config)
2. Leave BCA enabled (no config needed)
3. Add both to your structure_pool
4. BCA controls spawn locations, MVS provides variety

### Lithostitched Warnings

You may see warnings like:
```
[lithostitched/]: Couldn't find template pool reference: ctov:village/waystone/sand
```

These are harmless - Lithostitched is looking for optional structure pieces that may not exist.

### Multiple Mods Overriding Spacing

If spacing feels wrong, check for conflicts:
1. Better Village's `enabled_custom_config`
2. CristelLib's `config/vanilla_structures/placement_structure_config.json5`
3. Other mods with structure_set datapacks

Only one should control spacing. See [Spacing Guide](SpacingGuide.md).

---

**See Also:**
- [Configuration](Configuration.md) - Full config reference
- [Spacing Guide](SpacingGuide.md) - Village density control
- [Troubleshooting](Troubleshooting.md) - Common issues

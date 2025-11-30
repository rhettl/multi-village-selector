# Mod Compatibility Guide

This guide explains how to configure MVS to work with popular village mods.

## Table of Contents

- [Quick Reference](#quick-reference)
- [ChoiceTheorem's Overhauled Village (CTOV)](#choicetheorems-overhauled-village-ctov)
- [Better Village](#better-village)
- [Cobblemon Additions (BCA)](#cobblemon-additions-bca)
- [Towns & Towers](#towns--towers)
- [Terralith](#terralith)
- [Testing New Mods](#testing-new-mods)
- [Known Issues](#known-issues)

## Quick Reference

| Mod | Type | Disable Spawning? | Notes |
|-----|------|------------------|-------|
| [ChoiceTheorem's Overhauled Village](https://modrinth.com/mod/ct-overhaul-village) (CTOV) | Structure adder | No | MVS intercepts automatically |
| [Better Village](https://modrinth.com/mod/better-village-fabric) | Jigsaw replacer | Disable spacing only | **Critical:** Disable `enabled_custom_config` |
| [Luki's Grand Capitals](https://modrinth.com/datapack/lukis-grand-capitals) | Jigsaw replacer | No | Works alongside MVS automatically |
| [Cobblemon Additions](https://modrinth.com/mod/cobblemon-additions) (BCA) | Structure adder | No | MVS intercepts automatically |
| [Towns & Towers](https://modrinth.com/mod/towns-and-towers) | Structure adder | No | Included in generated config (own structure_set) |
| [Terralith](https://modrinth.com/mod/terralith) | Structure adder | No | Works out of the box |

## ChoiceTheorem's Overhauled Village (CTOV)

[Modrinth](https://modrinth.com/mod/ct-overhaul-village)

### No Configuration Needed

CTOV works out of the box with MVS. MVS automatically intercepts CTOV's village spawn attempts and applies your configured weights and biome rules.

### MVS Configuration

CTOV uses size-based structure IDs: `ctov:{size}/village_{type}`

**CTOV Weight Ratios:** 50:20:5 for small:medium:large (common → rare). 25 is MVS's target average weight, so 50 might be high for CTOV's many small villages.

```json5
structure_pool: [
  // CTOV plains villages (small = common, large = rare)
  { structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 50} },
  { structure: "ctov:medium/village_plains", biomes: {"#minecraft:has_structure/village_plains": 20} },
  { structure: "ctov:large/village_plains", biomes: {"#minecraft:has_structure/village_plains": 5} },

  // CTOV desert villages
  { structure: "ctov:small/village_desert", biomes: {"#minecraft:has_structure/village_desert": 50} },
  { structure: "ctov:medium/village_desert", biomes: {"#minecraft:has_structure/village_desert": 20} },
  { structure: "ctov:large/village_desert", biomes: {"#minecraft:has_structure/village_desert": 5} },

  // CTOV also adds villages to non-vanilla biomes (jungle, mesa, swamp, etc.)
  { structure: "ctov:small/village_jungle", biomes: {"#minecraft:is_jungle": 50} },
  { structure: "ctov:small/village_mesa", biomes: {"#minecraft:is_badlands": 50} },
  { structure: "ctov:small/village_swamp", biomes: {"#minecraft:has_structure/ruined_portal_swamp": 50} },
]
```

**Tip:** Use `/mvs generate` to get the full list of CTOV structures with correct biome tags.

## Better Village

[Modrinth](https://modrinth.com/mod/better-village-fabric)

### How Better Village Works

Better Village is a **jigsaw piece replacer**, not a structure adder. It replaces individual buildings inside vanilla villages with enhanced versions. MVS cannot control which Better Village pieces are used - it operates at a different level.

**What this means:**
- MVS selects *which village type* spawns (plains, desert, etc.)
- Better Village then modifies *what buildings* appear in that village, usually only in vanilla villages
- Both work together without conflict

### Critical: Disable Custom Spacing Config

**This is required!** Better Village overrides village spacing at runtime.

Edit `config/bettervillage_1.properties`:

```properties
# CRITICAL: Must be false for MVS to control village density
boolean.villages.enabled_custom_config=false
```

**Why:** Better Village modifies the `minecraft:villages` structure_set definition at runtime, overriding spacing/separation values. MVS intercepts structure selection, but spacing comes from the structure_set definition. Without disabling Better Village's config, your MVS spacing settings are ignored.

**Symptom:** Villages spawn unexpectedly far apart.

### MVS Configuration

Better Village structures use vanilla names, so they're already included if you have vanilla villages in your pool:

```json5
structure_pool: [
  { structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 25} },
  // Better Village automatically enhances these vanilla villages
]
```

**Note:** You don't need to add Better Village to your `structure_pool` - it enhances whatever vanilla villages MVS selects.

## Cobblemon Additions (BCA)

[Modrinth](https://modrinth.com/mod/cobblemon-additions)

### No Configuration Needed

BCA completely replaces the vanilla `minecraft:villages` structure_set via in-mod datapack. MVS automatically intercepts BCA spawn attempts.

### MVS Configuration

```json5
structure_pool: [
  // BCA default villages (spawn in vanilla village biomes)
  { structure: "bca:village/default_small", biomes: {"#bca:villages": 38} },
  { structure: "bca:village/default_mid", biomes: {"#bca:villages": 26} },
  { structure: "bca:village/default_large", biomes: {"#bca:villages": 5} },

  // BCA dark villages (dark forest, swamp, mushroom fields)
  { structure: "bca:village/dark_small", biomes: {"#bca:dark": 38} },
  { structure: "bca:village/dark_mid", biomes: {"#bca:dark": 26} },

  // BCA fighting villages (plains, savanna, meadow)
  { structure: "bca:village/fighting_small", biomes: {"#bca:fighting": 38} },
  { structure: "bca:village/fighting_mid", biomes: {"#bca:fighting": 26} },
  { structure: "bca:village/fighting_large", biomes: {"#bca:fighting": 5} },
]
```

**Note:** BCA structure names include `village/` in the path. The pattern `bca:default_*` won't match - use `bca:village/default_*`.

### BCA Intended Weights (v4.1.4+)

The config above uses MVS-normalized weights (targeting average 25). Below are BCA's raw weights from their structure_set for reference:

| Structure | Raw | Normalized | Frequency | Biomes |
|-----------|-----|------------|-----------|--------|
| default_small | 16 | 38 | Common | Vanilla village biomes |
| default_mid | 11 | 26 | Frequent | Vanilla village biomes |
| default_large | 2 | 5 | Rare | Vanilla village biomes |
| dark_small | 16 | 38 | Common | Dark forest, swamp, mushroom |
| dark_mid | 11 | 26 | Frequent | Dark forest, swamp, mushroom |
| fighting_small | 16 | 38 | Common | Plains, savanna, meadow |
| fighting_mid | 11 | 26 | Frequent | Plains, savanna, meadow |
| fighting_large | 2 | 5 | Rare | Plains, savanna, meadow |

### Known Issue: Chunk Palette Corruption

BCA's Pokecenter structure can occasionally cause chunk generation crashes due to how Cobblemon's PC block is stored. This is a BCA issue, not MVS. The BCA dev is aware.

## Towns & Towers

[Modrinth](https://modrinth.com/mod/towns-and-towers)

### Important: Separate Structure Set

Towns & Towers (T&T) uses its own structure_set `towns_and_towers:towns` (27 villages, spacing 51/12) instead of adding to `minecraft:villages`. This means T&T villages spawn 2.3x less frequently than vanilla on their own separate grid.

### Generated Config (Default)

`/mvs generate` automatically includes T&T:
- `towns_and_towers:towns` added to `block_structure_sets` (under LIKELY)
- T&T village structures added to `structure_pool` (uncommented)

This means MVS controls T&T village selection by default.

### Option: Let T&T Spawn Independently

If you prefer T&T to spawn on its own grid (in addition to MVS-controlled villages):

1. Remove `"towns_and_towers:towns"` from `block_structure_sets`
2. Remove/comment the T&T structures from `structure_pool`

T&T will then spawn independently at its native 51/12 spacing (2.3x rarer than vanilla).

**Note:** T&T uses custom biome tags (`#towns_and_towers:has_structure/*`), not vanilla tags.

## Terralith

[Modrinth](https://modrinth.com/mod/terralith)

### No Configuration Needed

Terralith's fortified villages work with MVS automatically.

### MVS Configuration

```json5
structure_pool: [
  // Terralith fortified villages (rare)
  { structure: "terralith:fortified_village", biomes: {"#terralith:has_structure/fortified_village": 14} },
  { structure: "terralith:fortified_desert_village", biomes: {"#terralith:has_structure/fortified_desert_village": 14} },
]
```

Lower weights (below average 25) preserve their intended rarity.

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

5. **Check for double spawning** - If the mod uses its own structure_set (not `minecraft:villages`), add it to `block_structure_sets` to prevent double density

## Known Issues

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

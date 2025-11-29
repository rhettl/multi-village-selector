# Village Spacing Guide

This guide explains how to control village density (how far apart villages spawn) using datapacks. MVS controls **which** villages spawn - spacing is handled separately by Minecraft's structure_set system.

## Table of Contents

- [Understanding Spacing](#understanding-spacing)
- [Default Values](#default-values)
- [Creating a Spacing Datapack](#creating-a-spacing-datapack)
- [Testing Configurations](#testing-configurations)
- [Compatibility Notes](#compatibility-notes)

## Understanding Spacing

Village density is controlled by three parameters in Minecraft's `structure_set` definition:

| Parameter | Description | Effect |
|-----------|-------------|--------|
| `spacing` | Maximum distance between villages (in chunks) | Higher = fewer villages |
| `separation` | Minimum distance between villages (in chunks) | Higher = more spread out |
| `salt` | Random seed modifier | Changes village grid position |

**How it works:**

Minecraft divides the world into a grid of cells, each `spacing` chunks wide. Within each cell, one village can spawn within a (spacing - separation) area, with `separation` chunks excluded from the positive X and Z edges. The `salt` offsets the grid to create variety.

**Visual example (spacing=34, separation=8):**

```
|<------------ 34 chunks ------------>|<------------ 34 chunks ------------>|
+----------------------------+--------+----------------------------+--------+
|                            |        |                            |        |
|                            |   8    |                            |   8    |
|     26x26 spawn area       | chunk  |     26x26 spawn area       | chunk  |
|                            |  excl  |                            |  excl  |
|         V can spawn        |  zone  |         V can spawn        |  zone  |
|           anywhere         |        |           anywhere         |        |
|            here            |        |            here            |        |
+----------------------------+--------+----------------------------+--------+
|    8 chunk exclusion zone  |        |    8 chunk exclusion zone  |        |
+----------------------------+--------+----------------------------+--------+
```

The separation value removes chunks from the **positive X and Z edges** of each cell, leaving a (spacing - separation) x (spacing - separation) area where villages can spawn.

## Default Values

**Vanilla Minecraft:**
- `spacing`: 34 chunks (~544 blocks)
- `separation`: 8 chunks (~128 blocks)
- `salt`: 10387312

**Result:** Villages roughly every 500-1000 blocks, never closer than 128 blocks.

## Creating a Spacing Datapack

To change village spacing, create a datapack that overrides the village structure_set.

### Step 1: Create Datapack Structure

```
your_world/datapacks/mvs_spacing/
├── pack.mcmeta
└── data/
    └── minecraft/
        └── worldgen/
            └── structure_set/
                └── villages.json
```

### Step 2: Create pack.mcmeta

```json
{
  "pack": {
    "pack_format": 48,
    "description": "MVS Village Spacing Override"
  }
}
```

**Note:** `pack_format` 48 is for Minecraft 1.21.1. Check [Minecraft Wiki](https://minecraft.wiki/w/Data_pack#Pack_format) for other versions.

### Step 3: Create villages.json

**Dense villages (for testing):**

```json
{
  "placement": {
    "type": "minecraft:random_spread",
    "spacing": 6,
    "separation": 3,
    "salt": 10387312
  },
  "structures": [
    {
      "structure": "minecraft:village_plains",
      "weight": 1
    },
    {
      "structure": "minecraft:village_desert",
      "weight": 1
    },
    {
      "structure": "minecraft:village_savanna",
      "weight": 1
    },
    {
      "structure": "minecraft:village_snowy",
      "weight": 1
    },
    {
      "structure": "minecraft:village_taiga",
      "weight": 1
    }
  ]
}
```

**Sparse villages (rare):**

```json
{
  "placement": {
    "type": "minecraft:random_spread",
    "spacing": 64,
    "separation": 16,
    "salt": 10387312
  },
  "structures": [
    {
      "structure": "minecraft:village_plains",
      "weight": 1
    }
  ]
}
```

**Important:** The `structures` list in the datapack doesn't matter when MVS is active - MVS uses your `structure_pool` config instead. But you must include at least one valid structure for the datapack to load.

### Step 4: Enable the Datapack

1. Save your datapack files
2. In Minecraft, run: `/datapack enable "file/mvs_spacing"`
3. Create a new world (or `/reload` for some settings)

## Testing Configurations

### Quick Testing Setup

For rapid testing, use dense spacing:

| Setting | Value | Villages Per Area |
|---------|-------|-------------------|
| spacing | 6 | Very dense (testing) |
| separation | 3 | Minimum gap |

**Usage:**
```json
"spacing": 6,
"separation": 3
```

### Recommended Configurations

**More villages than vanilla:**
```json
"spacing": 20,
"separation": 6
```

**Vanilla-like:**
```json
"spacing": 34,
"separation": 8
```

**Rare villages:**
```json
"spacing": 50,
"separation": 12
```

**Very rare (exploration reward):**
```json
"spacing": 80,
"separation": 20
```

### Separation Constraints

`separation` must be less than `spacing`. If separation equals or exceeds spacing, villages cannot spawn.

**Valid:** spacing=34, separation=8
**Invalid:** spacing=8, separation=34 (will error)

## Compatibility Notes

### Better Village Mod

**Critical:** Better Village overrides village spacing at runtime. If you're using Better Village, you must disable its custom config:

Edit `config/bettervillage_1.properties`:
```properties
boolean.villages.enabled_custom_config=false
```

Without this, your datapack spacing will be ignored.

### CristelLib

If you have CristelLib installed (comes with some mods), it provides a config at:

```
config/vanilla_structures/placement_structure_config.json5
```

This can also override village spacing. Check if this file exists and conflicts with your datapack.

### Other Village Mods

Some village mods create their own structure_sets:
- **CTOV**: Uses vanilla `minecraft:villages` (compatible)
- **Towns & Towers**: Has own structure_sets (may need separate overrides)
- **BCA**: Uses datapack override (may conflict)

If villages aren't spawning at expected density, check if another mod is overriding `minecraft:villages`.

## Quick Reference

**Download ready-made datapacks:**

- [village-spacing.zip](datapacks/village-spacing.zip) - Vanilla defaults (spacing=34, separation=8)
- [bca-villages.zip](datapacks/bca-villages.zip) - BCA 4.1.4 defaults (spacing=34, separation=10, triangular spread, 8 BCA villages)

Extract to your world's `datapacks/` folder, then edit `data/minecraft/worldgen/structure_set/villages.json` to customize.

**Common configurations:**

| Use Case | spacing | separation | Villages per 1000x1000 |
|----------|---------|------------|------------------------|
| Testing | 6 | 3 | ~25 |
| Dense | 16 | 4 | ~4 |
| Vanilla | 34 | 8 | ~1 |
| Sparse | 50 | 12 | <1 |
| Rare | 80 | 20 | Very rare |

---

**See Also:**
- [Configuration](Configuration.md) - MVS structure selection
- [Mod Compatibility](ModCompatibility.md) - Per-mod spacing notes
- [Troubleshooting](Troubleshooting.md) - Spacing issues

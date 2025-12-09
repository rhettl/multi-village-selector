# Village Spacing Guide

This guide explains how to control village density (how far apart villages spawn) using MVS's placement config.

**Keep in mind:** Spacing, frequency, weight, and biomes **all** play a part in village selection and density. See [Configuration](Configuration.md) for the full picture.

## Table of Contents

- [Understanding Spacing](#understanding-spacing)
- [Configuring Placement](#configuring-placement)
- [Quick Reference](#quick-reference)
- [Using Datapacks Instead](#using-datapacks-instead)

## Understanding Spacing

Village distance is controlled by these parameters:

| Parameter | Description                     | Effect |
|-----------|---------------------------------|--------|
| `spacing` | Grid cell size in chunks        | Higher = fewer villages |
| `separation` | Minimum chunks between villages | Higher = more spread out |
| `spreadType` | How to choose WHERE village goes | See [Spread Types](SpreadTypes.md) |
| `salt` | Random seed modifier            | Changes village grid position |

**How it works:**

Villages default to spacing=34 and separation=8. Here's what that means:

The world is divided into a grid of squares (cells). Each cell is 34x34 chunks (~544×544 blocks). At most one village can spawn per cell.

The separation (8 chunks) creates a buffer zone on the edges where villages can't spawn. This keeps villages in neighboring cells from ending up right next to each other.

Lower spacing = more villages. Lower separation = villages can spawn closer to cell edges.

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

### Spread Type

Once the game picks a cell, `spreadType` determines WHERE inside the cell the village lands. The vanilla game offers two options: `linear` (equal chance anywhere) and `triangular` (biased toward center). MVS adds more options.

See the **[Spread Types Guide](SpreadTypes.md)** for visual diagrams and all available options.

## Configuring Placement

As of v0.4.0, MVS controls placement directly in your config. No datapacks needed.

### Basic Example

```json5
{
  placement: {
    "minecraft:villages": {
      spacing: 34,
      separation: 8,
    }
  }
}
```

### Common Configurations

In most cases, vanilla's 34/8 is recommended. If you have large villages, you might consider slightly more sparse with something like 40/16 or 50/12.

**Very dense (for testing):**
```json5
placement: {
  "minecraft:villages": {
    spacing: 6,
    separation: 3,
  }
}
```

**Dense (more villages):**
```json5
placement: {
  "minecraft:villages": {
    spacing: 20,
    separation: 6,
  }
}
```

**Vanilla-like:**
```json5
placement: {
  "minecraft:villages": {
    spacing: 34,
    separation: 8,
  }
}
```

**Sparse (rare villages):**
```json5
placement: {
  "minecraft:villages": {
    spacing: 50,
    separation: 12,
  }
}
```

### Constraints

- `separation` must be less than `spacing`
- **Valid:** spacing=34, separation=8
- **Valid:** spacing=34, separation=33
- **Invalid:** spacing=8, separation=34 (villages cannot spawn)
- **Invalid:** spacing=34, separation=34 (villages cannot spawn)

### Verifying Your Settings

Use `/mvs info` to see the current placement values:

```
/mvs info
  ⚡ minecraft:villages
      spacing: 34 (config), separation: 8 (config), salt: 10387312 (registry)
```

## Quick Reference

| Use Case | spacing | separation | Cell Size |
|----------|---------|------------|-----------|
| Testing | 6 | 3 | ~96 blocks |
| Dense | 16 | 4 | ~256 blocks |
| Dense | 20 | 6 | ~320 blocks |
| Vanilla | 34 | 8 | ~544 blocks |
| Sparse | 50 | 12 | ~800 blocks |
| Very Sparse | 80 | 20 | ~1280 blocks |

**Note:** Cell size = spacing × 16 blocks. Villages spawn at most once per cell.

## Using Datapacks Instead

If you prefer to use a datapack instead of MVS's placement config, you can still do that. Datapacks give you more control but require more setup.

**When to use datapacks:**
- You want to share spacing settings without MVS
- You need to control structure_sets MVS doesn't intercept
- You're already familiar with datapacks

### Datapack Structure

```
your_world/datapacks/mvs_spacing/
├── pack.mcmeta
└── data/
    └── minecraft/
        └── worldgen/
            └── structure_set/
                └── villages.json
```

### pack.mcmeta

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Village Spacing Override"
  }
}
```

**Note:** `pack_format` 48 is for Minecraft 1.21.1. Check [Minecraft Wiki](https://minecraft.wiki/w/Data_pack#Pack_format) for other versions.

### villages.json Example

```json
{
  "placement": {
    "type": "minecraft:random_spread",
    "spacing": 34,
    "separation": 8,
    "salt": 10387312
  },
  "structures": [
    { "structure": "minecraft:village_plains", "weight": 1 },
    { "structure": "minecraft:village_desert", "weight": 1 },
    { "structure": "minecraft:village_savanna", "weight": 1 },
    { "structure": "minecraft:village_snowy", "weight": 1 },
    { "structure": "minecraft:village_taiga", "weight": 1 }
  ]
}
```

**Note:** When MVS is active, it ignores the `structures` list and uses your `structure_pool` config instead. But you must include at least one valid structure for the datapack to load.

### Enabling the Datapack

1. Save files to `your_world/datapacks/mvs_spacing/`
2. Run `/datapack enable "file/mvs_spacing"`
3. Create a new world (spacing applies on world creation)

---

**See Also:**
- [Configuration](Configuration.md#placement) - Full placement config
- [Spread Types](SpreadTypes.md) - Visual guide to spread types
- [Mod Compatibility](ModCompatibility.md) - Per-mod notes

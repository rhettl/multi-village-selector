# Configuration Guide

This guide covers the MVS configuration file format for v0.3.0+. The config uses [JSON5](https://json5.org/), which supports comments and trailing commas.

## Table of Contents

- [Quick Reference](#quick-reference)
- [Config Location](#config-location)
- [Basic Config](#basic-config)
- [Structure Pool](#structure-pool)
- [Biome Weights](#biome-weights)
- [Pattern Matching](#pattern-matching)
- [Biome Frequency](#biome-frequency)
- [Structure Sets](#structure-sets)
- [Advanced Options](#advanced-options)
- [Complete Example](#complete-example)

## Quick Reference

```json5
{
  enabled: true,                    // Master enable/disable
  debug_logging: false,             // Log spawn attempts
  debug_cmd: false,                 // Enable debug commands

  intercept_structure_sets: [       // Which structure sets MVS controls
    "minecraft:villages"
  ],

  structure_pool: [                 // Your village structures
    { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} }
  ],

  biome_frequency: {                // Spawn rate per biome (optional)
    "#minecraft:is_ocean": 0.3
  },

  blacklisted_structures: [],       // Structures to never spawn
  block_structure_sets: []          // Structure sets to completely disable
}
```

## Config Location

```
<minecraft>/config/multivillageselector.json5
```

MVS creates a default config on first launch. Use `/mvs generate` to create a complete config based on your installed mods.

## Basic Config

The simplest useful config intercepts villages and defines a structure pool:

```json5
{
  enabled: true,

  intercept_structure_sets: ["minecraft:villages"],

  structure_pool: [
    { structure: "minecraft:village_plains", biomes: {"*:*": 10} },
    { structure: "minecraft:village_desert", biomes: {"*:*": 10} },
    { structure: "minecraft:village_taiga", biomes: {"*:*": 10} },
    { structure: "minecraft:village_savanna", biomes: {"*:*": 10} },
    { structure: "minecraft:village_snowy", biomes: {"*:*": 10} }
  ]
}
```

This makes all vanilla villages spawn in all biomes with equal weight.

## Structure Pool

The `structure_pool` array defines which structures can spawn and where.

### Basic Structure Entry

```json5
{ structure: "minecraft:village_plains", biomes: {"*:*": 10} }
```

- `structure` - Full structure ID (`namespace:structure_name`)
- `biomes` - Map of biome patterns to weights

### Finding Structure IDs

Use `/mvs generate` to scan your mods, or check mod documentation. Common formats:

- Vanilla: `minecraft:village_plains`
- CTOV: `ctov:village_plains`, `ctov:large/village_desert`
- BCA: `bca:village/default_plains`
- Towns & Towers: `towns_and_towers:village_plains`

## Biome Weights

The `biomes` object maps biome patterns to spawn weights. Higher weight = more likely to spawn.

### Weight Basics

```json5
biomes: {
  "#minecraft:is_plains": 10,    // Weight 10 in plains biomes
  "#minecraft:is_forest": 5,     // Weight 5 in forest biomes
  "*:*": 1                       // Weight 1 everywhere else
}
```

### How Weights Work

When MVS selects a structure:

1. Finds all structures valid for current biome
2. Sums their weights
3. Randomly selects based on weight

**Example:** Three structures with weights 10, 5, 5 (total 20):
- Structure A (10): 50% chance
- Structure B (5): 25% chance
- Structure C (5): 25% chance

### Biome Patterns

| Pattern | Matches | Example |
|---------|---------|---------|
| `minecraft:plains` | Exact biome ID | Only `minecraft:plains` |
| `#minecraft:is_plains` | Biome tag | All plains biomes |
| `*:plains` | Wildcard namespace | Any mod's `plains` biome |
| `minecraft:*` | All biomes from namespace | All vanilla biomes |
| `*:*` | Everything | Universal fallback |

### Pattern Priority

More specific patterns take priority:

1. **Exact biome ID** - `minecraft:plains` (most specific)
2. **Biome tag** - `#minecraft:is_plains`
3. **Partial wildcard** - `minecraft:*`
4. **Full wildcard** - `*:*` (least specific)

### Common Biome Tags

Vanilla Minecraft provides these tags:

- `#minecraft:is_plains` - Plains variants
- `#minecraft:is_desert` - Deserts
- `#minecraft:is_forest` - Forests
- `#minecraft:is_taiga` - Taiga/cold forests
- `#minecraft:is_savanna` - Savannas
- `#minecraft:is_ocean` - All oceans
- `#minecraft:is_beach` - Beaches
- `#minecraft:is_mountain` - Mountains
- `#minecraft:is_badlands` - Badlands/mesa

### Biome-Specific Example

```json5
structure_pool: [
  // Desert village ONLY in deserts
  {
    structure: "minecraft:village_desert",
    biomes: {
      "#minecraft:is_desert": 10,
      "#minecraft:is_badlands": 5  // Also badlands, lower weight
    }
  },

  // Plains village in plains and forests
  {
    structure: "minecraft:village_plains",
    biomes: {
      "#minecraft:is_plains": 10,
      "#minecraft:is_forest": 8
    }
  }
]
```

## Pattern Matching

Structure IDs support wildcards for matching multiple structures.

### Wildcard Patterns

```json5
structure_pool: [
  // Match all CTOV villages
  { structure: "ctov:*", biomes: {"*:*": 10} },

  // Match CTOV small villages only
  { structure: "ctov:small/*", biomes: {"*:*": 10} },

  // Match any mod's village_plains
  { structure: "*:village_plains", biomes: {"*:*": 10} }
]
```

### Pattern Expansion

Patterns expand at config load time. MVS scans the registry and replaces:

```json5
{ structure: "ctov:small/*", biomes: {"*:*": 10} }
```

With actual structures:

```json5
{ structure: "ctov:small/village_plains", biomes: {"*:*": 10} },
{ structure: "ctov:small/village_desert", biomes: {"*:*": 10} },
{ structure: "ctov:small/village_taiga", biomes: {"*:*": 10} },
// ... etc
```

### Combining with Biome Rules

```json5
{
  // All CTOV plains villages in plains biomes
  structure: "ctov:*/village_*plains*",
  biomes: {"#minecraft:is_plains": 10}
}
```

## Biome Frequency

Control how often structures spawn at all in certain biomes.

```json5
biome_frequency: {
  "#minecraft:is_ocean": 0.3,     // 30% spawn rate in oceans
  "#minecraft:is_plains": 1.0,    // 100% in plains (default)
  "*:*": 0.5                      // 50% everywhere else
}
```

### How It Works

Before selecting a structure, MVS rolls against biome frequency:

1. Look up frequency for current biome (0.0 - 1.0)
2. Generate random number
3. If random > frequency, skip this spawn attempt entirely
4. If random <= frequency, proceed with structure selection

### Use Cases

**Rare ocean villages:**
```json5
biome_frequency: {
  "#minecraft:is_ocean": 0.1,  // Only 10% of ocean spawn attempts succeed
  "*:*": 1.0
}
```

**Dense plains villages:**
```json5
biome_frequency: {
  "#minecraft:is_plains": 1.0,  // All spawn attempts in plains succeed
  "*:*": 0.5                    // Other biomes: 50%
}
```

## Structure Sets

### Intercepting Structure Sets

`intercept_structure_sets` tells MVS which structure sets to control:

```json5
intercept_structure_sets: [
  "minecraft:villages"  // Standard village structure set
]
```

When MVS intercepts a structure set, it:
1. Ignores the vanilla structure list
2. Uses your `structure_pool` instead
3. Applies your biome rules

### Blocking Structure Sets

`block_structure_sets` completely disables structure sets:

```json5
block_structure_sets: [
  "some_mod:custom_villages"  // Never generates from this set
]
```

Use this when a mod creates its own village structure set that conflicts with MVS.

## Advanced Options

### Debug Logging

```json5
{
  debug_logging: true,  // Log all spawn attempts to latest.log
  debug_cmd: true       // Enable /mvs debug commands
}
```

Debug output in logs:

```
[MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
[MVS] Generation FAILED: minecraft:village_desert - biome mismatch (plains)
[MVS] Biome frequency roll FAILED for #minecraft:is_ocean (0.30)
```

### Blacklisting Structures

Prevent specific structures from ever spawning:

```json5
blacklisted_structures: [
  "some_mod:ugly_village",
  "another_mod:broken_structure"
]
```

Blacklisted structures are removed from the pool before selection.

### Weight Normalization

When using `/mvs generate`, MVS normalizes weights so each mod gets equal representation:

- Each mod's structures average to weight 25
- Internal ratios preserved (if mod has 10:5:2 ratio, that's maintained)
- Rarity factors capped at 10.0 to prevent ultra-rare structures from being invisible

This happens automatically - you only need to know if you're manually editing weights.

## Complete Example

A full config for vanilla + CTOV + BCA:

```json5
{
  // Master settings
  enabled: true,
  debug_logging: false,
  debug_cmd: false,

  // What MVS controls
  intercept_structure_sets: ["minecraft:villages"],
  block_structure_sets: [],

  // Spawn frequency per biome
  biome_frequency: {
    "#minecraft:is_ocean": 0.2,      // Rare ocean villages
    "#minecraft:is_plains": 1.0,     // Common plains villages
    "*:*": 0.7                        // Default: 70%
  },

  // Structure pool
  structure_pool: [
    // === Vanilla Villages ===
    { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} },
    { structure: "minecraft:village_desert", biomes: {"#minecraft:is_desert": 10} },
    { structure: "minecraft:village_taiga", biomes: {"#minecraft:is_taiga": 10} },
    { structure: "minecraft:village_savanna", biomes: {"#minecraft:is_savanna": 10} },
    { structure: "minecraft:village_snowy", biomes: {"#minecraft:is_snowy": 10} },

    // === CTOV Villages ===
    { structure: "ctov:village_plains", biomes: {"#minecraft:is_plains": 10} },
    { structure: "ctov:village_desert", biomes: {"#minecraft:is_desert": 10} },
    { structure: "ctov:village_taiga", biomes: {"#minecraft:is_taiga": 10} },
    { structure: "ctov:village_savanna", biomes: {"#minecraft:is_savanna": 10} },
    { structure: "ctov:village_snowy", biomes: {"#minecraft:is_snowy": 10} },

    // === BCA Villages (Cobblemon) ===
    { structure: "bca:village/default_plains", biomes: {"#minecraft:is_plains": 8} },
    { structure: "bca:village/default_desert", biomes: {"#minecraft:is_desert": 8} },
    { structure: "bca:village/default_taiga", biomes: {"#minecraft:is_taiga": 8} },
  ],

  // Never spawn these
  blacklisted_structures: []
}
```

---

**See Also:**
- [Getting Started](GettingStarted.md) - First-time setup
- [Mod Compatibility](ModCompatibility.md) - Per-mod configuration
- [Commands](Commands.md) - In-game inspection commands
- [Troubleshooting](Troubleshooting.md) - Common issues

# Configuration Reference

Config file: `config/multivillageselector.json5` (JSON5 format - comments allowed)

## Quick Start

```json5
{
  enabled: true,
  intercept_structure_sets: ["minecraft:villages"],
  structure_pool: [
    { structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 25} },
  ],
}
```

Generate a complete config: `/mvs generate` → outputs to `local/mvs/multivillageselector.json5`

**Note:** Spacing, frequency, weight, and biomes **all** play a part in village selection and density.

---

## Config Fields

| Field | Type | Default | Description                                                    |
|-------|------|---------|----------------------------------------------------------------|
| `enabled` | boolean | `true` | Master enable/disable                                          |
| `show_launch_message` | boolean | `false` | Show startup message in logs on world load                     |
| `block_structure_sets` | string[] | `[]` | Structure sets to completely disable                           |
| `intercept_structure_sets` | string[] | `[]` | Structure sets MVS controls (usually `["minecraft:villages"]`) |
| `structure_pool` | object[] | `[]` | Structures available for spawning ([details](#structure_pool)) |
| `blacklisted_structures` | string[] | `[]` | Structure IDs to never spawn                                   |
| `biome_frequency` | object | `{}` | Spawn rate multiplier per biome ([details](#biome_frequency))  |
| `placement` | object | `{}` | Override structure placement settings ([details](#placement)) |
| `relaxed_biome_validation` | boolean | `false` | Bypass vanilla's biome check ([details](#relaxed_biome_validation)) |
| `debug_cmd` | boolean | `false` | Enable `/mvs debug` commands                                   |
| `debug_logging` | boolean | `false` | Log spawn attempts to `latest.log`                             |

---

## structure_pool

Array of structure entries defining what can spawn and where.

### Entry Format

```json5
structure_pool: [
  { structure: "<id>", biomes: { "<pattern>": <weight>, ... } },
  { structure: "<pattern>", biomes: { "<pattern>": <weight>, ... } },
]
```

| Field | Type | Description |
|-------|------|-------------|
| `structure` | string | Structure ID (`minecraft:village_plains`) or pattern (`ctov:small/*`) |
| `biomes` | object | Map of biome pattern → spawn weight (higher = more common) |

### Structure IDs

| Mod | Format | Examples |
|-----|--------|----------|
| Vanilla | `minecraft:village_<type>` | `minecraft:village_plains`, `minecraft:village_desert` |
| CTOV | `ctov:<size>/village_<type>` | `ctov:small/village_plains`, `ctov:large/village_desert` |
| BCA | `bca:village/<type>_<size>` | `bca:village/default_small`, `bca:village/fighting_mid` |
| T&T | `towns_and_towers:village_<biome>` | `towns_and_towers:village_badlands` |

Use `/mvs generate` to discover structure IDs from installed mods.

### Biome Patterns

Patterns match biomes for weight assignment. More specific patterns win.

| Pattern | Matches | Specificity |
|---------|---------|-------------|
| `minecraft:plains` | Exact biome ID | Highest |
| `#minecraft:has_structure/village_plains` | Biome tag | High |
| `minecraft:*` | All biomes in namespace | Medium |
| `*:*` | All biome IDs | 1 (hardcoded) |
| `#*:*` | All biome tags | 0 (hardcoded) |

**Note:** The `#` prefix matches biome **tags** instead of IDs. Tags have lower specificity than IDs, so `#*:*` (any tag) is less specific than `*:*` (any ID). This applies to both `biome_frequency` and `structure_pool[].biomes`.

### Common Biome Tags

**Village spawning:**
- `#minecraft:has_structure/village_plains`
- `#minecraft:has_structure/village_desert`
- `#minecraft:has_structure/village_taiga`
- `#minecraft:has_structure/village_savanna`
- `#minecraft:has_structure/village_snowy`

**General:**
- `#minecraft:is_forest`, `#minecraft:is_taiga`, `#minecraft:is_jungle`
- `#minecraft:is_ocean`, `#minecraft:is_beach`, `#minecraft:is_mountain`, `#minecraft:is_badlands`

Use `/mvs biome tags` in-game to see tags for current biome.

### Weights

Weights are relative within a biome. Target average: **25**.

```json5
// In plains: A=50%, B=33%, C=17%
{ structure: "A", biomes: {"#minecraft:has_structure/village_plains": 30} },
{ structure: "B", biomes: {"#minecraft:has_structure/village_plains": 20} },
{ structure: "C", biomes: {"#minecraft:has_structure/village_plains": 10} },
```

**Weights are per-biome.** Structures only compete with others in the same biome:

```json5
// Plains structures (compete with each other)
{ structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 25} },
{ structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 50} },
{ structure: "bca:village/default_mid", biomes: {"#minecraft:has_structure/village_plains": 25} },
// → In plains: vanilla=25%, ctov=50%, bca=25%

// Desert structures (separate competition)
{ structure: "minecraft:village_desert", biomes: {"#minecraft:has_structure/village_desert": 40} },
{ structure: "ctov:small/village_desert", biomes: {"#minecraft:has_structure/village_desert": 60} },
// → In desert: vanilla=40%, ctov=60%
```

The plains weights (25, 50, 25) don't affect desert selection, and vice versa.


**Structures can have weights in multiple biomes:**

```json5
// This structure spawns in plains (weight 25) AND desert (weight 5)
{ structure: "minecraft:village_plains", biomes: {
  "#minecraft:has_structure/village_plains": 25,  // Competes in plains
  "#minecraft:has_structure/village_desert": 5,   // Also competes in desert (rare)
} },
{ structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 50} },
{ structure: "bca:village/default_mid", biomes: {"#minecraft:has_structure/village_plains": 25} },

{ structure: "minecraft:village_desert", biomes: {"#minecraft:has_structure/village_desert": 40} },
{ structure: "ctov:small/village_desert", biomes: {"#minecraft:has_structure/village_desert": 60} },

// In plains (total=100): village_plains=25%, ctov_plains=50%, bca=25%
// In desert (total=105): village_plains=5%, village_desert=38%, ctov_desert=57%
```


### Wildcards

Structure patterns expand at load time:

```json5
{ structure: "ctov:small/*", biomes: {"*:*": 10} }
// Expands to:
//   ctov:small/village_plains  -> any-biome: 10
//   ctov:small/village_desert  -> any-biome: 10
//   ctov:small/village_taiga   -> any-biome: 10
//   ...
```

---

## biome_frequency

**Frequency:** The probability (0.0-1.0) that a spawn attempt proceeds. At 0.5, half of spawn attempts are skipped entirely.

Controls spawn rate per biome. Applied before structure selection.

```json5
biome_frequency: {
  "#minecraft:is_ocean": 0.1,   // 10% of ocean spawn attempts proceed
  "#minecraft:is_forest": 1.0,  // 100% in forests
  "*:*": 0.5                    // 50% default
}
```

**Note:** Unmentioned biomes default to 100% frequency (implied `"#*:*": 1.0`).

Same pattern matching as `biomes` in structure_pool.

---

## placement

*Added in v0.4.0*

Override the placement algorithm for any structure set. When empty (`{}`), MVS uses registry values from the original structure set.

### Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `spacing` | int | (registry) | Grid cell size in chunks. Villages spawn at most once per cell. |
| `separation` | int | (registry) | Minimum chunks between villages. Must be < spacing. |
| `salt` | int | (registry) | Seed modifier for placement RNG. Change to shift the grid. |
| `spreadType` | string | (registry) | Distribution pattern within cells. See below. |
| `strategy` | string | `"random_spread"` | Placement algorithm. Currently only `random_spread` supported. |
| `exclusion_zone` | object | (registry) | Keep structures away from another set. See below. |

### Spread Types

| Type | Distribution | Status                           |
|------|--------------|----------------------------------|
| `linear` | Uniform random anywhere in cell | Stable (vanilla villages use this) |
| `triangular` | Bell curve biased toward cell center | Stable (vanilla option) |
| `gaussian` | Strong center bias with rare edge spawns | *Experimental*                   |
| `edge_biased` | Biased toward cell edges | *Experimental*                   |
| `corner_biased` | Pushed toward cell corners | *Experimental*                   |
| `fixed_center` | Always at exact cell center (deterministic) | *Experimental*                   |

*Experimental spread types are implemented but less tested. Report issues on [GitHub](https://github.com/RhettL/multi-village-selector/issues).*

For visual diagrams of each spread type, see the **[Spread Types Guide](SpreadTypes.md)**.

### Exclusion Zone

Prevents structures from spawning near another structure set. Vanilla uses this for pillager outposts (won't spawn within 10 chunks of villages).

```json5
exclusion_zone: {
  other_set: "minecraft:villages",  // Structure set to avoid
  chunk_count: 10                   // Minimum chunks away (1-16)
}
```

### Example

Keys are structure set IDs. Each set can have its own placement settings:

```json5
{
  intercept_structure_sets: ["minecraft:villages"],

  placement: {
    // Villages cannot spawn within 8 chunks of desert temples
    "minecraft:villages": {
      spacing: 34,
      separation: 8,
      spreadType: "triangular",
      exclusion_zone: {
        other_set: "minecraft:desert_temples",
        chunk_count: 8
      }
    },
    // Jungle temples cannot spawn within 10 chunks of villages
    "minecraft:jungle_temples": {
      // spacing: will inherit
      // separation: will inherit
      exclusion_zone: {
        other_set: "minecraft:villages",
        chunk_count: 10
      }
    }
  }
}
// Note: Exclusion zones are one-directional.
// Villages avoid desert temples, jungle temples avoid villages.
// But desert temples can still spawn next to jungle temples!
```

### Spacing Quick Reference

| Density | Spacing | Separation | Avg Distance |
|---------|---------|------------|--------------|
| Very Dense | 10 | 4 | ~160 blocks |
| Dense | 20 | 8 | ~320 blocks |
| Default | 34 | 8 | ~544 blocks |
| Sparse | 50 | 12 | ~800 blocks |
| Very Sparse | 80 | 20 | ~1280 blocks |

**Keep in mind:** Spacing, frequency, weight, and biomes **all** play a part in village selection and density.

For detailed explanations, formulas, and recommendations, see the **[Spacing Guide](SpacingGuide.md)**.

### Empty Placement

When `placement: {}` or omitted, MVS reads values from the registry:

```
/mvs info
  ⚡ minecraft:villages
      spacing: 34 (registry), separation: 8 (registry), salt: 10387312 (registry)
```

---

## relaxed_biome_validation

Controls whether vanilla's secondary biome validation is enforced. **Use this when** you are using tall biome mods like Terralith or Tectonic (steeper slopes mean biomes change at different elevations), or mods with large structures like BCA or CTOV large villages.

**Background:** When MVS selects a structure, it samples the biome in the chunk. However, vanilla performs a second biome check at the structure's *bounding box center* after jigsaw assembly. For large structures, these positions can differ significantly, causing the structure to be rejected even though MVS selected it.

| Structure Type | Starter Size | BB Center Offset | Issue Likelihood |
|----------------|--------------|------------------|------------------|
| Vanilla villages | 9-15 blocks | +4 to +8 | Low |
| CTOV small/medium | 10-20 blocks | +5 to +10 | Low |
| BCA default_mid | 37×38 blocks | +18, +19 | Medium |
| BCA academy | 49×73 blocks | +24, +36 | High |

**When `false` (default):**
- Vanilla validates biome at the structure's bounding box center
- Works well for vanilla-sized structures
- May reject large mod structures whose bounding box center lands in a different biome

**When `true`:**
- Bypass vanilla's biome check entirely
- Trust MVS's chunk-center selection
- Recommended for modpacks with large village structures (BCA, CTOV large, etc.)

```json5
// Recommended for modpacks with BCA, large CTOV villages, etc.
relaxed_biome_validation: true,
```

---

## Structure Sets

### intercept_structure_sets

MVS takes control of structure selection for these sets. Usually just `["minecraft:villages"]`.

**Note:** MVS placement settings only work with `RandomSpreadStructurePlacement` structures (villages, pillager outposts, most modded structures). Strongholds use `ConcentricRingsStructurePlacement` with ring-based logic that MVS cannot control.

### block_structure_sets

Completely disables these structure sets. Use when mods have their own village sets that would cause double-spawning.

```json5
block_structure_sets: [
  "some_mod:custom_villages",      // Blocked - structures in pool spawn via minecraft:villages instead
  "towns_and_towers:towns",        // Block T&T's native set, use MVS pool
]
```

---

## blacklisted_structures

Array of structure IDs to never spawn, even if they match the pool.

```json5
blacklisted_structures: [
  "some_mod:ugly_village",
  "bca:village/broken_structure",
]
```

---

## Debug

```json5
debug_cmd: true,      // Enables `/mvs debug` commands
debug_logging: true,  // Logs to latest.log:
                      //   [MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
                      //   [MVS] Biome frequency roll FAILED for #minecraft:is_ocean (0.10)
```

---

## Complete Example

```json5
{
  enabled: true,

  block_structure_sets: ["towns_and_towers:towns"],
  intercept_structure_sets: ["minecraft:villages"],

  structure_pool: [
    // Vanilla
    { structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 25} },
    { structure: "minecraft:village_desert", biomes: {"#minecraft:has_structure/village_desert": 25} },

    // CTOV (50:20:5 ratio for small:medium:large)
    { structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 50} },
    { structure: "ctov:medium/village_plains", biomes: {"#minecraft:has_structure/village_plains": 20} },
    { structure: "ctov:large/village_plains", biomes: {"#minecraft:has_structure/village_plains": 5} },

    // BCA
    { structure: "bca:village/default_small", biomes: {"#bca:villages": 38} },
    { structure: "bca:village/default_mid", biomes: {"#bca:villages": 26} },
  ],

  blacklisted_structures: [
    "ctov:large/village_desert"  // Too big for my taste
  ],

  biome_frequency: {
    "minecraft:deep_ocean": 0.1,
    "#minecraft:is_ocean": 0.2,
    "#*:*": 1.0
  },

  // Optional: control village density (vanilla defaults shown)
  placement: {
    "minecraft:villages": {
      spacing: 34,
      separation: 8,
    }
  },

  // Recommended true for BCA and other large structure mods
  relaxed_biome_validation: true,

  debug_cmd: false,
  debug_logging: false,
}
```

---

**See Also:** [Getting Started](GettingStarted.md) | [Mod Compatibility](ModCompatibility.md) | [Commands](Commands.md) | [Troubleshooting](Troubleshooting.md)

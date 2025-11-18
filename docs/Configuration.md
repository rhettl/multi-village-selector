# Configuration Guide

Multi Village Selector uses a JSON5 configuration file located at `config/multivillageselector.json5`. This file is automatically created with sensible defaults on first launch.

**Important:** Configuration is loaded at server/game startup. Changes to the config file require a full restart to take effect - `/reload` does NOT reload MVS configuration.

---

## Quick Start: Auto-Generate Config

The easiest way to configure MVS is to use the **`/mvs generate`** command in-game:

1. **Launch Minecraft** with MVS and your village mods installed
2. **Run `/mvs generate`** in-game (requires OP level 2)
3. **Click the file link** in chat to open `local/mvs/multivillageselector.json5`
4. **Review the generated config** - adjust weights and biome categories as desired
5. **Copy to `config/multivillageselector.json5`** to replace your current config
6. **Restart Minecraft** for changes to take effect

### What `/mvs generate` Does

- ✅ **Scans all installed mods** for village structures
- ✅ **Detects common village mods** (BCA, CTOV, Towns & Towers, etc.)
- ✅ **Categorizes villages by biome** based on structure names
- ✅ **Creates `replace_of` list** (vanilla + BCA if present)
- ✅ **Creates `prevent_spawn` list** (all modded villages)
- ✅ **Generates balanced weights** (equal weight of 10 for all villages)
- ✅ **Identifies uncategorized biomes** (suggests overrides in comments)

The generated config is a **starting point** - you should review and adjust:
- **Weights**: Increase weight for villages you want to see more often
- **Biome categories**: Add overrides for modded biomes in the UNCATEGORIZED section
- **Pool variety**: Remove villages you don't want, add patterns for new mods

See the rest of this guide for detailed explanations of each configuration option.

---

## Table of Contents

- [Quick Start: Auto-Generate Config](#quick-start-auto-generate-config)
- [Basic Settings](#basic-settings)
- [Structure Interception](#structure-interception)
  - [replace_of](#replace_of)
  - [prevent_spawn](#prevent_spawn)
- [Replacement Pools](#replacement-pools)
  - [Entry Types](#entry-types)
  - [Biome Categories](#biome-categories)
- [Advanced Configuration](#advanced-configuration)
  - [Weighted Empty Spawns](#weighted-empty-spawns)
  - [Biome Overrides](#biome-overrides)
    - [Biome Detection Hierarchy](#biome-detection-hierarchy)
- [Pattern Matching](#pattern-matching)
- [Examples](#examples)
- [Command Reference](#command-reference)

---

## Basic Settings

```json5
{
  enabled: true,
  debug_logging: false,
  show_first_launch_message: true, // Only in bundled default config
}
```

### `enabled`
- **Type:** Boolean
- **Default:** `true`
- **Description:** Enable or disable the entire mod. When false, villages spawn normally without MVS intervention.

### `debug_logging`
- **Type:** Boolean
- **Default:** `false`
- **Description:** Enables detailed logging for troubleshooting. Shows:
  - Structure interception attempts
  - Biome detection results
  - Selection pool composition
  - Weighted random selection results

**Recommended:** Enable only when debugging issues. Creates verbose logs.

### `show_first_launch_message`
- **Type:** Boolean
- **Default:** `true` (only in bundled default config)
- **Description:** Controls whether the first-launch welcome message appears in server logs.
  - When `true`: Shows a helpful quick-start guide on first server launch, then automatically sets to `false`
  - When `false`: No welcome message is shown
  - **Note:** Generated configs from `/mvs generate` do not include this field (treated as `false`)

**Purpose:** Provides new users with setup instructions without being intrusive. The message only appears once and includes:
- Quick start guide (4 steps)
- Link to GitHub documentation
- Confirmation that it won't show again

---

## Structure Interception

MVS intercepts village spawn attempts using two mechanisms:

###  `replace_of`

**Purpose:** Structures to intercept and replace. This will expand the spawning pools.

```json5
replace_of: [
  "minecraft:village_plains",
  "minecraft:village_desert",
  "minecraft:village_savanna",
  "minecraft:village_snowy",
  "minecraft:village_taiga",
]

// Alternate ...
replace_of: [
  "minecraft:village_*",
]
```

#### When to use
- Vanilla villages (likely always)
- Mods that override vanilla structure sets (e.g., BCA, mods that overwrite vanilla algorithm)

#### How it works
1. MVS detects spawn attempt for listed structure
2. Samples biome at surface level of spawn location
3. Selects replacement from appropriate biome pool
4. Places selected structure
5. Marks location as "processed" to prevent duplicates (uses LRU cache, max 1000 entries)

###  `prevent_spawn`

**Purpose:** Structures to block from spawning naturally. this will condense the spawning pool.

```json5
prevent_spawn: [
  "ctov:large/*",
  "ctov:medium/*",
  "ctov:small/*",
  "towns_and_towers:village_*",
]
```

#### When to use
- Standard village mods that spawn in addition to vanilla (e.g. CTOV, Towns & Towers, Better Villages, Create New Beginnings)

#### Best Practice
If a mod provides a config option to disable village spawning (like CTOV's `generatesmallVillage = false`), **prefer using the mod's config** instead of `prevent_spawn`. This is cleaner and more reliable. Only use `prevent_spawn` when:
- The mod has no config option to disable villages
- The config only disables the entire mod (not just villages)
- You want fine-grained control (e.g., block only certain village types)

**Example:** CTOV has config toggles → disable in CTOV config ✅\
**Example:** Mod with no village toggle → add to `prevent_spawn` ✅

**Note:** Some mods (like BCA) override villages at the structure set level and should use `replace_of` instead. See [Mod Compatibility](ModCompatibility.md) for details.

#### How it works
1. MVS detects spawn attempt for listed structure
2. Cancels spawn immediately
3. Does NOT mark location as processed; Does not check `replace_of` list.
4. Allows vanilla villages to attempt spawn at same location

Structures in `prevent_spawn` can still be selected as replacements in `replace_with` pools. This is intended.

**Important:** Entries are NOT intended to be in both `prevent_spawn` AND `replace_of`. `prevent_spawn` overrides `replace_of`

---

## Replacement Pools

Replacement pools define which villages can spawn in each biome category.

### Entry Types

MVS supports three types of pool entries:

##### 1. Specific Structure
```json5
{ structure: "minecraft:village_plains", weight: 10 }
```
Matches exactly one structure by full resource location.

##### 2. Wildcard Pattern
```json5
{ pattern: "ctov:*/village_plains", weight: 30 }
```
Matches multiple structures using wildcards (`*`). See [Pattern Matching](#pattern-matching).

##### 3. Weighted Empty
```json5
{ empty: true, weight: 60 }
```
Cancels spawning with specified weight. See [Weighted Empty Spawns](#weighted-empty-spawns).

### Biome Categories

Structures are organized by biome category for appropriate selection:

```json5
replace_with: {
  plains: [
    { structure: "minecraft:village_plains", weight: 10 },
    { pattern: "towns_and_towers:village_*plains", weight: 30 },
    { pattern: "ctov:*/village_plains", weight: 30 },
  ],

  desert: [
    { structure: "minecraft:village_desert", weight: 10 },
    { pattern: "ctov:*/village_desert", weight: 30 },
  ],

  // ... more categories
}
```

#### Available categories
- `plains` - Plains, meadows, forests, birch forests
- `desert` - Desert, badlands, mesa
- `savanna` - Savanna, shattered savanna
- `snowy` - Snowy plains, ice spikes, frozen peaks, groves
- `taiga` - Taiga, old growth taiga, snowy taiga
- `swamp` - Swamp, mangrove swamp
- `jungle` - Jungle, bamboo jungle
- `ocean` - All ocean biomes
- `beach` - Beach, stony shore
- `mushroom` - Mushroom fields
- `dark_forest` - Dark forest
- `DEFAULT` - Fallback for uncategorized biomes

**Note:** `DEFAULT` pool is not included in other categories. if you want something in both, put it in both. 
But if category doesn't exist, it will use DEFAULT, Ex: 

```json5
replace_with: {
  plains: [
    { structure: "minecraft:village_plains", weight: 10 },
    { pattern: "towns_and_towers:village_*plains", weight: 30 },
    { pattern: "ctov:*/village_plains", weight: 30 },
  ],

  DEFAULT: [
    { pattern: "minecraft:village_plains", weight: 30 },
    { empty: true, weight: 90 },
  ],
}
```

in this case, if an `ocean` biome is selected, a `minecraft:village_plains` has a 1/3 chance to appear there -- because we didn't set an `ocean` category in our config.

---

## Advanced Configuration

### Weighted Empty Spawns

Control village density per biome by adding weighted "no-spawn" entries. This is for cases like ocean floating villages,
villager ships, etc. where you want oceans to be mostly empty, but still roll against the pool: 

```json5
ocean: [
  { pattern: "joshie:village_ocean", weight: 20 },
  { pattern: "towns_and_towers:village_ocean", weight: 20 },
  { empty: true, weight: 60 }, // 60% chance no village spawns
]
```

#### How it works
- Total weight: 20 + 20 + 60 = 100
- Ocean village chance: 40% (20 + 20 out of 100)
- No spawn chance: 60%

**Result:** Ocean villages spawn at 40% normal density

#### Use cases
- **Ocean biomes:** Few suitable villages, should be rare
- **Mushroom biomes:** Very rare, mystical biomes
- **Any sparse biome** where full density feels wrong

#### Example configurations
```json5
// Rare ocean villages
ocean: [
  { pattern: "*:village_ocean", weight: 30 },
  { empty: true, weight: 70 },
]

// Very rare mushroom villages
mushroom: [
  { pattern: "*:village_mushroom*", weight: 10 },
  { empty: true, weight: 90 },
]

// Moderate desert villages
desert: [
  { pattern: "*:village_desert*", weight: 60 },
  { empty: true, weight: 40 },
]
```

### Biome Overrides

Override the automatic biome category detection with **highest priority**:

```json5
biome_category_overrides: {
  "minecraft:deep_ocean": "ocean",
  "minecraft:warm_ocean": "ocean",
  "terralith:volcanic_peaks": "snowy",
}
```

#### How it works
- Checked **FIRST** before any automatic detection
- Overrides name matching, tags, and temperature checks
- Uses exact biome resource location (e.g., `"minecraft:plains"`, not just `"plains"`)

#### Biome Detection Hierarchy

MVS categorizes biomes using multiple methods in this priority order:

1. **User Config Overrides** (Highest Priority)
   - Checks `biome_category_overrides` first
   - If biome ID is found, uses that category immediately

2. **Biome Tags**
   - Checks Minecraft biome tags (e.g., `IS_OCEAN`)
   - Reliable for vanilla and well-tagged modded biomes

3. **Name Matching**
   - Extensive keyword matching on biome resource location
   - Checks for keywords like "jungle", "swamp", "mushroom", "desert", "taiga", etc.
   - Supports many modded biomes automatically

4. **Temperature Fallback**
   - If no match above, checks biome temperature:
     - `coldEnoughToSnow()` → `snowy`
     - `temp > 1.5f` → `desert`
     - `temp > 1.0f` → `savanna`
     - `temp < 0.3f` → `taiga`
   - Works for all biomes but less precise

5. **Default to Plains**
   - If all methods fail, categorizes as `plains`

**Example:** Terralith's "volcanic_peaks" biome:
- Not in overrides → check tags
- No ocean tag → check name
- Name contains "peaks" but not specific keywords → check temperature
- Temperature is hot → categorized as `desert`
- To fix: Add to overrides → `"terralith:volcanic_peaks": "snowy"`

#### When to use
- Modded biomes that aren't automatically categorized correctly
- Biomes you want to treat differently than their name suggests
- Force specific villages in specific biomes

#### How to find biome IDs
1. Enable `debug_logging: true`
2. Find a village with wrong biome assignment
3. Check logs for: `Biome: terralith:volcanic_peaks (at Y=150) → Category: desert`
4. Add to overrides: `"terralith:volcanic_peaks": "snowy"`

**Example:** Terralith's volcanic peaks might be hot, but you want snowy alpine villages there for the aesthetic.

---

## Pattern Matching

Patterns use wildcards (`*`) to match multiple structures:

### Pattern Syntax

**`*` matches anything:**
```json5
"ctov:*/village_plains"
```
Matches:
- `ctov:small/village_plains` ✅
- `ctov:medium/village_plains` ✅
- `ctov:large/village_plains` ✅

**Multiple wildcards:**
```json5
"towns_and_towers:village_*"
```
Matches:
- `towns_and_towers:village_plains` ✅
- `towns_and_towers:village_desert` ✅
- `towns_and_towers:village_ocean` ✅
- `towns_and_towers:village_anything` ✅

**Wildcards anywhere:**
```json5
"*:village_ocean"
```
Matches:
- `joshie:village_ocean` ✅
- `towns_and_towers:village_ocean` ✅
- `anymod:village_ocean` ✅

### Pattern Specificity

When multiple patterns match the same structure, **the most specific pattern wins**:

```json5
plains: [
  { pattern: "ctov:*/village_*", weight: 20 },           // Less specific
  { pattern: "ctov:*/village_plains", weight: 30 },      // More specific
  { pattern: "ctov:large/village_plains", weight: 50 },  // Most specific
]
```

For `ctov:large/village_plains`:
- Matches all three patterns
- Uses weight **50** (most specific)

**Specificity = pattern length minus wildcards**

### Pattern Best Practices

1. **Use wildcards for families of villages:**
   ```json5
   { pattern: "ctov:*/village_plains", weight: 30 }
   ```

2. **Use specific structures for special cases:**
   ```json5
   { structure: "terralith:fortified_village", weight: 20 }
   ```

3. **Combine for flexibility:**
   ```json5
   plains: [
     { pattern: "ctov:*/village_plains", weight: 30 },
     { pattern: "towns_and_towers:exclusives/*", weight: 15 },
     { structure: "minecraft:village_plains", weight: 10 },
   ]
   ```

---

## Examples

### Minimal Configuration (Vanilla Only)

```json5
{
  enabled: true,
  debug_logging: false,

  replace_of: [
    "minecraft:village_plains",
    "minecraft:village_desert",
    "minecraft:village_savanna",
    "minecraft:village_snowy",
    "minecraft:village_taiga",
  ],

  prevent_spawn: [],

  replace_with: {
    DEFAULT: [
      { structure: "minecraft:village_plains", weight: 20 },
      { structure: "minecraft:village_desert", weight: 20 },
      { structure: "minecraft:village_savanna", weight: 20 },
      { structure: "minecraft:village_snowy", weight: 20 },
      { structure: "minecraft:village_taiga", weight: 20 },
    ]
  },

  biome_category_overrides: {}
}
```

### Full Modpack Configuration

```json5
{
  enabled: true,
  debug_logging: false,

  replace_of: [
    "minecraft:village_plains",
    "minecraft:village_desert",
    "minecraft:village_savanna",
    "minecraft:village_snowy",
    "minecraft:village_taiga",
    "bca:village/default_small",
    "bca:village/default_mid",
    "bca:village/default_large",
    "bca:village/dark_mid",
    "bca:village/dark_small",
  ],

  prevent_spawn: [
    "ctov:large/*",
    "ctov:medium/*",
    "ctov:small/*",
    "towns_and_towers:village_*",
    "towns_and_towers:exclusives/*",
    "terralith:fortified_*_village",
  ],

  replace_with: {
    plains: [
      { structure: "minecraft:village_plains", weight: 10 },
      { pattern: "towns_and_towers:village_*plains", weight: 30 },
      { pattern: "towns_and_towers:village_*forest", weight: 30 },
      { pattern: "bca:village/default_*", weight: 25 },
      { pattern: "ctov:*/village_plains", weight: 30 },
      { pattern: "towns_and_towers:exclusives/*", weight: 15 },
      { structure: "terralith:fortified_village", weight: 20 },
    ],

    ocean: [
      { pattern: "joshie:village_ocean", weight: 20 },
      { pattern: "towns_and_towers:village_ocean", weight: 20 },
      { empty: true, weight: 60 },
    ],

    mushroom: [
      { pattern: "*:village_mushroom*", weight: 10 },
      { empty: true, weight: 90 },
    ],

    // ... more categories
  },

  biome_category_overrides: {}
}
```

### Testing Configuration (High Density)

```json5
{
  enabled: true,
  debug_logging: true,  // Enable for testing!

  replace_of: [
    "minecraft:village_plains",
    "minecraft:village_desert",
    "minecraft:village_savanna",
    "minecraft:village_snowy",
    "minecraft:village_taiga",
  ],

  prevent_spawn: [
    "ctov:large/*",
    "ctov:medium/*",
    "ctov:small/*",
  ],

  replace_with: {
    DEFAULT: [
      { structure: "minecraft:village_plains", weight: 50 },
      { pattern: "ctov:*/village_*", weight: 50 },
    ]
  },

  biome_category_overrides: {}
}
```

**Note:** Also set village spacing to 6/3 in structure placement configs for rapid testing!\
If you have cristel lib installed, you can find the config at `<instance>/config/vanilla_structures/placement_structure_config.json5`

```json5
{
  "villages": {
    "salt": 10387312,
    "separation": 3, // min 3 chunks between village starting points
    "spacing": 6     // max 6 chunks between village starting points
  },
  /* Other configs ... */
}
```

---

## Troubleshooting Config

Full troubleshooting guide at [this guide.](Troubleshooting.md)

### Villages not spawning
1. Enable `debug_logging: true`
2. Create new world
3. Check logs for `[MVS-DEBUG] Structure attempt:` messages
4. Verify structures are attempting to spawn

### Wrong villages in wrong biomes
1. Check biome category assignments
2. Add specific overrides in `biome_category_overrides`
3. Verify pattern matching with debug logging

### Duplicate villages:
1. Check both `replace_of` and `prevent_spawn` lists
2. Make sure modded villages aren't spawning naturally
3. See [Mod Compatibility](ModCompatibility.md) for mod-specific config

### Config not loading:
1. Check JSON5 syntax (commas, brackets, quotes)
2. Look for parse errors in logs
3. Delete config and let it regenerate from default
4. Run `/mvs generate` to create a fresh config

### Config validation warnings:
MVS validates your config on startup and will skip invalid entries with warnings:

**Missing weight field:**
```
⚠️  MVS Config Warning: Entry in category 'plains' is missing 'weight' field: minecraft:village_plains - SKIPPING
```
**Fix:** Add `weight: X` to the entry

**Weight zero or negative:**
```
⚠️  MVS Config Warning: Entry in category 'plains' has weight <= 0: minecraft:village_plains (weight: 0) - SKIPPING (will never be selected)
```
**Fix:** Use positive weight (1 or higher)

**Missing content field:**
```
⚠️  MVS Config Warning: Entry in category 'plains' has weight but no structure/pattern/empty field - SKIPPING
```
**Fix:** Add one of: `structure: "..."`, `pattern: "..."`, or `empty: true`

**Missing root property:**
```
⚠️  MVS Config Warning: 'enabled' field missing - defaulting to true
```
**Fix:** Add missing field or use `/mvs generate`

**Malformed config:**
```
╔════════════════════════════════════════════════════════╗
║  ❌ FAILED TO LOAD MVS CONFIG                         ║
║  Your config file is MALFORMED and cannot be parsed.  ║
╚════════════════════════════════════════════════════════╝
```
**Fix:** Check JSON5 syntax, run `/mvs generate`, or delete config to regenerate

**Note:** If entries are skipped, the mod will continue running with remaining valid entries. Check logs for warnings after each restart.

---

## Command Reference

MVS provides several in-game commands to help you configure and debug your setup:

### Configuration Commands

- **`/mvs generate`** - Auto-generate a config file based on your installed mods
  - Scans for village structures
  - Creates smart defaults for `replace_of`, `prevent_spawn`, and `replace_with`
  - Identifies uncategorized biomes
  - Outputs to `local/mvs/multivillageselector.json5` with clickable link

### Debugging Commands

- **`/mvs biome`** - Show your current biome, location, temperature, and category
- **`/mvs biome <biome_id>`** - Look up which category a specific biome maps to
- **`/mvs biome list`** - List all biomes grouped by category (shows UNCATEGORIZED biomes)
- **`/mvs pools`** - List all configured pool categories
- **`/mvs pools <category>`** - Show structures in a pool with weights and percentages

### Common Use Cases

**Initial Setup:**
```
/mvs generate          → Generate starter config
/mvs biome list        → Check which biomes need overrides
```

**Verify Configuration:**
```
/mvs pools plains      → Check plains village weights
/mvs biome             → Verify biome categorization at your location
```

**Debug Villages:**
```
/mvs biome             → Check category at village location
/mvs pools <category>  → See which villages can spawn
```

For complete command documentation, see [Commands Reference](Commands.md).

---

## Next Steps

- See [Mod Compatibility](ModCompatibility.md) for per-mod configuration
- See [Troubleshooting](Troubleshooting.md) for common issues

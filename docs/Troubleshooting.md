# Troubleshooting Guide

Common issues and solutions for Multi Village Selector.

## Table of Contents

- [Villages Not Spawning](#villages-not-spawning)
- [Wrong Villages in Wrong Biomes](#wrong-villages-in-wrong-biomes)
- [Only One Mod's Villages Spawning](#only-one-mods-villages-spawning)
- [Too Many/Too Few Villages](#too-manytoo-few-villages)
- [Config Not Loading](#config-not-loading)
- [Structure Errors in Logs](#structure-errors-in-logs)
- [Debug Mode](#debug-mode)

---

## Villages Not Spawning

### Symptom
No villages generating in new worlds, or very sparse village spawning.

### Diagnosis Workflow

#### Step 1: Enable Debug Logging

Edit `config/multivillageselector.json5`:
```json5
{
  enabled: true,
  debug_logging: true,  // Set to true
  // ...
}
```

#### Step 2: Create New World & Explore

- Create a brand new world (old worlds don't regenerate)
- Explore 1000+ blocks from spawn (spawn zone has no structures)
- Check logs for MVS activity

#### Step 3: Check Logs

Look for these patterns in `logs/latest.log`:

✅ **Good - MVS is working:**
```
[MVS-DEBUG] Structure attempt: minecraft:village_plains at chunk [X, Z]
===== MVS: INTERCEPTED =====
Original: minecraft:village_plains
Biome: minecraft:plains (at Y=75) → Category: plains
SELECTED: ctov:large/village_plains
✅ Successfully placed...
===== MVS: SUCCESS =====
```

❌ **Bad - No village attempts:**
```
// Empty - no MVS debug messages at all
```

### Common Causes & Solutions

#### Cause 1: CTOV/BCA Not Disabled Properly

##### If using CTOV

Check `config/ctov-common.toml`:
```toml
[structures]
    generatesmallVillage = false  # Must be false
    generatemediumVillage = false  # Must be false
    generatelargeVillage = false  # Must be false
```

##### Solution
Edit config, restart Minecraft, create NEW world

#### Cause 2: BCA Overriding But Not in replace_of

If you see in logs:
```
[MVS-DEBUG] Structure attempt: bca:village/default_small
```
But no interception, BCA villages aren't in your `replace_of` list.

##### Solution
Add BCA villages to `replace_of`:
```json5
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
]
```

#### Cause 3: Weighted Empty Selected

If you see in logs:
```
SELECTED: (empty - cancelling spawn)
===== MVS: SPAWN CANCELLED (EMPTY) =====
```

This is intentional! You have weighted empty spawns in your pool.

##### Solution
Reduce `empty` weights or remove them:
```json5
ocean: [
  { pattern: "joshie:village_ocean", weight: 50 },
  { empty: true, weight: 50 },  // Reduce this or remove
]
```

#### Cause 4: No Structures Available for Biome

If you see in logs:
```
No replacement structures available for biome category: mushroom
```

##### Solution
Add structures to that biome category or add a DEFAULT fallback:
```json5
replace_with: {
  DEFAULT: [
    { structure: "minecraft:village_plains", weight: 10 },
    // ... fallback villages
  ],
  // ... other categories
}
```

---

## Wrong Villages in Wrong Biomes

### Symptom
Desert villages spawning in snow, ocean villages spawning on land, etc.

### Diagnosis

**Enable debug logging** and check biome detection:
```
Biome: minecraft:desert (at Y=68) → Category: desert
```

### Common Causes & Solutions

#### Cause 1: Biome Detection Mismatch

Some modded biomes aren't automatically categorized correctly.

##### Solution
Use `biome_category_overrides`:
```json5
biome_category_overrides: {
  "terralith:volcanic_peaks": "snowy",
  "terralith:hot_spring": "taiga",
  "modname:weird_biome": "plains",
}
```

##### How to find biome IDs

- Enable debug logging
- Find a village in the wrong biome
- Check logs for: `Biome: terralith:volcanic_peaks`
- Add that biome ID to overrides

#### Cause 2: Using DEFAULT Pool

If you see:
```
No structures for desert, using DEFAULT
```

You don't have a `desert` category defined, so MVS falls back to DEFAULT pool.

##### Solution
Add specific biome categories or adjust your DEFAULT pool to have appropriate variety.

---

## Only One Mod's Villages Spawning

### Symptom
Only seeing CTOV villages, or only BCA villages, no variety.

### Diagnosis

Check your `replace_with` pools and weights:
```
Selection Pool for plains (24 structures, total weight: 585):
  - minecraft:village_plains (weight: 10, 1.7%)
  - ctov:large/village_plains (weight: 500, 85.5%)  # TOO HIGH!
```

### Solutions

#### Solution 1: Balance Weights

Lower dominant mod's weights:
```json5
plains: [
  { structure: "minecraft:village_plains", weight: 10 },
  { pattern: "ctov:*/village_plains", weight: 30 },  # Not 500!
  { pattern: "towns_and_towers:village_*plains", weight: 30 },
]
```

#### Solution 2: Check Pattern Matching

Patterns might be too broad or not matching:
```json5
{ pattern: "ctov:*/village_*", weight: 30 }  // Matches EVERYTHING from CTOV!
```

vs

```json5
{ pattern: "ctov:*/village_plains", weight: 30 }  // Only plains variants
```

#### Solution 3: Verify Mods Are Installed

If you reference mod structures that aren't installed, they won't spawn:
```
ERROR: Replacement structure not found in registry: terralith:fortified_village
```

##### Solution
Install the mod or remove from config

---

## Too Many/Too Few Villages

### Symptom
Villages every 100 blocks, or villages every 5000 blocks.

### Diagnosis

Village density is controlled by:
1. **Structure spacing configs** (not MVS)
2. **Weighted empty spawns** (MVS feature)

### Solutions

#### For Too Many Villages

##### Option 1: Adjust vanilla structure spacing

Edit `config/vanilla_structures/placement_structure_config.json5`:
```json5
"villages": {
  "salt": 10387312,
  "separation": 8,    // Min distance between villages (chunks)
  "spacing": 34       // Max distance between village attempts (chunks)
}
```

Increase spacing: `34 → 50` = villages further apart

##### Option 2: Use weighted empty spawns

```json5
plains: [
  { pattern: "*:village_*plains", weight: 40 },
  { empty: true, weight: 60 },  // 60% chance no village spawns
]
```

#### For Too Few Villages

- Check if weighted empties are too high
- Check structure spacing isn't too large
- Verify villages are actually attempting to spawn (debug logs)

---

## Config Not Loading

### Symptom
Changes to config don't take effect, or mod uses defaults.

### Solutions

#### Solution 1: Restart Minecraft

Config is only loaded at startup. Changes require full restart. `/reload` does NOT reload MVS configuration.

#### Solution 2: Check JSON5 Syntax

Common syntax errors:
```json5
// WRONG - missing comma
{
  enabled: true
  debug_logging: false
}

// CORRECT
{
  enabled: true,
  debug_logging: false,
}
```

```json5
// WRONG - trailing comma in array
replace_of: [
  "minecraft:village_plains",
]

// CORRECT - no trailing comma, or...
replace_of: [
  "minecraft:village_plains"
]

// ALSO CORRECT - trailing comma is fine in JSON5!
replace_of: [
  "minecraft:village_plains",
]
```

Wait, JSON5 DOES support trailing commas. The issue is:

```json5
// WRONG - unquoted key with special chars
biome-category: "plains"

// CORRECT
"biome-category": "plains"
// OR
biome_category: "plains"  // Underscores are fine unquoted
```

#### Solution 3: Check File Location

Config must be at:
```
minecraft/config/multivillageselector.json5
```

NOT:
- `mods/multivillageselector/multivillageselector.json5`
- `config/mvs_config.json5` (old name)

#### Solution 4: Delete and Regenerate

If config is corrupted:
1. Delete `config/multivillageselector.json5`
2. Restart Minecraft
3. Mod will create fresh default config

---

## Structure Errors in Logs

### Symptom
```
[minecraft/StructureTemplateManager]: Couldn't load structure ctov:village/desert_oasis/jobsite/butcher
java.lang.RuntimeException: Unknown block type 'minecraft:gross_block'
```

### Diagnosis

This is a **structure mod bug**, not MVS!

MVS successfully:
- ✅ Selected the structure
- ✅ Placed the structure

The structure mod provided:
- ❌ Invalid structure template (bad block ID)

### Solutions

- **Ignore it:** Village still mostly generates, just missing one piece
- **Report to structure mod:** Let CTOV/Towns & Towers/etc know about invalid blocks
- **Not an MVS issue:** MVS did its job, structure quality is mod's responsibility

---

## Debug Mode

### Enabling Debug Mode

Edit `config/multivillageselector.json5`:
```json5
{
  enabled: true,
  debug_logging: true,  // Enable this
  // ...
}
```

### What Debug Mode Shows

#### Structure Interception
```
[MVS-DEBUG] Structure attempt: minecraft:village_plains at chunk [10, 20]
```
Shows every village spawn attempt MVS sees.

#### Interception Decision
```
===== MVS: INTERCEPTED =====
Original: minecraft:village_plains
Location: X=160, Z=320
```
MVS decided to replace this village.

#### Biome Detection
```
Biome: minecraft:plains (at Y=75) → Category: plains
```
Shows detected biome and category assignment.

#### Selection Pool
```
Selection Pool for plains (24 structures, total weight: 585):
  - minecraft:village_plains (weight: 10, 1.7%)
  - ctov:large/village_plains (weight: 30, 5.1%)
  - towns_and_towers:village_sunflower_plains (weight: 30, 5.1%)
  ...
```
All available villages for this biome with their weights and probabilities.

#### Random Selection
```
Random Roll: 287 out of 585 (seed: 1234567890)
SELECTED: ctov:large/village_plains
```
Shows the random roll and what was selected.

#### Placement Result
```
✅ Successfully placed ctov:large/village_plains at [160, 320]
===== MVS: SUCCESS =====
```
Or:
```
ERROR: Replacement structure not found in registry: modname:village
===== MVS: REPLACEMENT FAILED =====
```

#### Empty Selection
```
SELECTED: (empty - cancelling spawn)
===== MVS: SPAWN CANCELLED (EMPTY) =====
```
Weighted empty was selected, no village placed intentionally.

### Using Debug Logs

#### To diagnose "no villages"
1. Search logs for `[MVS-DEBUG] Structure attempt:`
2. If none → vanilla villages aren't spawning (check CTOV/BCA configs)
3. If present → follow the log chain to see what happened

#### To diagnose "wrong biomes"
1. Find village spawn in logs
2. Check `Biome: X → Category: Y`
3. If category wrong → use `biome_category_overrides`

#### To diagnose "no variety"
1. Check `Selection Pool` logs
2. Look at weights and percentages
3. Adjust weights if one mod dominates

---

## Still Having Issues?

If none of these solutions work:

1. **Enable debug logging**
2. **Create a new world**
3. **Explore 1000+ blocks** from spawn
4. **Copy last 200 lines of logs:**
   ```bash
   tail -200 logs/latest.log > mvs-debug.txt
   ```
5. **Open an issue:** [GitHub Issues](https://github.com/RhettL/multi-village-selector/issues)
   - Include your config file
   - Include the debug log output
   - Describe what you expected vs what happened
   - List which village mods you have installed

---

## Quick Checklist

Before opening an issue, verify:

- ✅ Debug logging enabled
- ✅ Created NEW world (not old world)
- ✅ Explored beyond spawn zone (300+ blocks)
- ✅ CTOV village generation disabled (if using CTOV)
- ✅ BCA villages in `replace_of` (if using BCA)
- ✅ Config syntax valid (no JSON errors)
- ✅ Minecraft restarted after config changes
- ✅ Mod installed in `mods/` folder
- ✅ Using correct Minecraft version (1.21.1)

---

**See also:**
- [Configuration Guide](Configuration.md) - Complete config reference
- [Mod Compatibility](ModCompatibility.md) - Per-mod setup instructions

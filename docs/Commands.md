# Commands Reference

All commands require **OP level 2** on dedicated servers. Single-player allows all commands.

## Command Overview

| Command | Description |
|---------|-------------|
| `/mvs` or `/mvs help` | Show command help |
| `/mvs info` | Show MVS status, intercepted sets, pool size |
| `/mvs generate` | Generate config from installed mods |
| `/mvs config reload` | Reload config without restart |

### Biome Commands

Commands for inspecting biome information.

| Command | Description |
|---------|-------------|
| `/mvs biome` | Current biome info + frequency |
| `/mvs biome tags [biome]` | List tags for current or specified biome |
| `/mvs biome by-tag <tag>` | Find all biomes with a tag |
| `/mvs biome similar [biome]` | Find biomes with similar tags |

### Test Commands

Commands for testing MVS pool selection.

| Command | Description |
|---------|-------------|
| `/mvs test biome [biome]` | Eligible structures + simulated selection roll |
| `/mvs test structure <id>` | Check if structure is MVS-controlled/blocked |

### Structure Commands

Commands related to structure information.

| Command | Description |
|---------|-------------|
| `/mvs structure pool [full]` | List structures in MVS config pool |
| `/mvs structure list [filter]` | Dump all game structures to file |
| `/mvs structure biomes <id> [full]` | Show biome rules for structure |
| `/mvs structure test <structure> <biome>` | Test if structure spawns in biome |
| `/mvs structure nearby [radius]` | Find structures near player (default: 100 chunks) |
| `/mvs structure set <id> [full]` | Inspect a structure set |

---

## /mvs generate

Scans installed mods and generates a complete config.

After running, a description of where the file is located and a link to open it will appear

**Output:** `local/mvs/multivillageselector.json5`

**What it does:**
1. Scans all registered structures
2. Detects village mods (CTOV, BCA, T&T, etc.)
3. Categorizes structures into how likely they're villages (probably, likely, questionable, unlikely)
4. Generates biome rules from registry
5. Normalizes weights (target average: 25)
6. Filters out the 'Unlikely' structures
7. Comments out the questionable structures
8. Enables MVS for the probably and likely structures

**Workflow:**
```
/mvs generate
→ Review local/mvs/multivillageselector.json5
→ Copy to config/multivillageselector.json5
→ Restart Minecraft (or /mvs config reload)
```

**Important:** Always review the config generated. As the user, you are in control of weights and structures. The generated config is a recommendation and a **guess** at best. You are responsible for the weights and structure selections -- MVS can only follow your direction.

---

## /mvs info

Shows MVS status at a glance.

```
=== Multi Village Selector v0.3.1 ===

Status: ENABLED
Pool Size: 47 structures

Blocked Structure Sets: 3
  ⛔ bca:villages
  ⛔ towns_and_towers:towns

Intercepted Structure Sets: 1
  ⚡ minecraft:villages
      spacing: 34, separation: 8, salt: 10387312

→ View Structure Pool (clickable)
```

---

## /mvs biome

Shows current biome info including MVS frequency.

```
=== Current Biome ===
Location: X: 1234, Y: 72, Z: -5678
Biome: minecraft:plains
Tags: #minecraft:has_structure/village_plains, #minecraft:is_overworld
Biome Frequency: 1.0 (100%)
```

### /mvs biome tags [biome]

List all tags for a biome. Omit biome to use current location.

```
/mvs biome tags minecraft:dark_forest

Tags for minecraft:dark_forest:
  #minecraft:is_forest
  #minecraft:is_overworld
  #minecraft:has_structure/woodland_mansion
```

### /mvs biome by-tag \<tag\>

Find all biomes that have a specific tag.

```
/mvs biome by-tag #minecraft:is_forest

Biomes with tag #minecraft:is_forest:
  minecraft:forest
  minecraft:flower_forest
  minecraft:dark_forest
  minecraft:birch_forest
  ...
```

---

## /mvs test

Test commands for debugging spawn rules.

### /mvs test biome [biome]

Shows eligible structures and **runs a simulated selection roll**.

```
/mvs test biome minecraft:plains

=== Testing MVS Selection: minecraft:plains ===

Eligible structures (filtered pool):
  minecraft:village_plains (weight: 25)
  ctov:small/village_plains (weight: 50)
  ctov:medium/village_plains (weight: 20)

Result: ctov:small/village_plains
  Weight: 50
```

Run multiple times to see weighted distribution in action.

### /mvs test structure \<id\>

Shows structure status: MVS-controlled, blacklisted, structure_set membership.

```
/mvs test structure ctov:small/village_desert

=== MVS Structure Status: ctov:small/village_desert ===

✓ MVS Controlled: YES
  This structure is in the MVS structure_pool

✓ Blacklisted: NO

Structure Set: minecraft:villages
  ⚡ Structure set is INTERCEPTED by MVS
```

---

## /mvs structure

Commands for inspecting structure configuration.

### /mvs structure pool [full]

List all structures in your MVS config pool. Add `full` for detailed output.

```
/mvs structure pool

=== MVS Structure Pool ===

Structures: 12 | Empty entries: 0

  minecraft:village_plains (1 biome tags)
  minecraft:village_desert (1 biome tags)
  ctov:small/village_plains (1 biome tags)
  ... and 9 more
```

### /mvs structure list [filter]

Dumps ALL structures in the game to a file, grouped by namespace and structure_set.

**Output:** `local/mvs/structure-list-<timestamp>.txt`

**Filter:** Optional pattern to filter structures (no `#` biome tags allowed)
- `/mvs structure list` - All structures
- `/mvs structure list bca:*` - Only BCA structures
- `/mvs structure list *village*` - Structures with "village" in name

**Example output file:**
```
================================================================================
MVS Structure List - All Structures in Game
Generated: 2025-12-01 09:30:15
Total: 156 structures
================================================================================

=== minecraft (23 structures) ===

  [minecraft:villages]
    minecraft:village_desert
      Biomes: #minecraft:has_structure/village_desert
      weight=1
    minecraft:village_plains
      Biomes: #minecraft:has_structure/village_plains
      weight=1
    ...

=== bca (8 structures) ===

  [bca:villages]
    bca:village/dark_mid
      Biomes: #minecraft:is_dark_forest
      weight=11
    ...
```

### /mvs structure biomes \<id\> [full]

Shows biome rules for a structure.

```
/mvs structure biomes minecraft:village_plains

=== Structure: minecraft:village_plains ===
Source: MVS Config

Biome Rules:
  #minecraft:has_structure/village_plains: weight 25
```

### /mvs structure test \<structure\> \<biome\>

Tests if a structure can spawn in a specific biome.

```
/mvs structure test minecraft:village_desert minecraft:plains

Result: NO
Reason: minecraft:plains does not match any biome rules
```

### /mvs structure nearby [radius]

Finds MVS-controlled structures near the player. Default radius: 100 chunks.

```
/mvs structure nearby 50

Structures within 50 chunks:
  minecraft:village_plains at [12, -5] (320 blocks)
  ctov:small/village_plains at [-8, 22] (544 blocks)
```

### /mvs structure set \<id\> [full]

Inspects a structure set's contents and placement settings.

```
/mvs structure set minecraft:villages

=== Structure Set: minecraft:villages ===
Spacing: 34, Separation: 8, Salt: 10387312

Structures:
  minecraft:village_plains (weight: 2)
  minecraft:village_desert (weight: 2)
  ...
```

---

## Quick Reference

```bash
# Status
/mvs info

# Generate config
/mvs generate

# Biome info
/mvs biome
/mvs biome tags
/mvs biome tags minecraft:dark_forest
/mvs biome by-tag #minecraft:is_forest

# Test spawning
/mvs test biome
/mvs test biome minecraft:plains
/mvs test structure minecraft:village_plains

# Structure inspection
/mvs structure list
/mvs structure biomes minecraft:village_plains
/mvs structure test minecraft:village_desert minecraft:plains
/mvs structure nearby 100
/mvs structure set minecraft:villages

# Config
/mvs config reload

# Debug (requires debug_cmd: true)
/mvs debug mod-scan
/mvs debug mod-scan all
/mvs debug profiler start
```

---

## Debug Commands

Require `debug_cmd: true` in config.

### /mvs debug mod-scan [all]

Scans mods for structures and outputs detailed analysis.

**Output:** `local/mvs/mod-scan-<timestamp>.txt`

- Without `all`: Only village-related structures
- With `all`: Every structure from every structure set

### /mvs debug profiler

Performance profiling for structure generation.

```
/mvs debug profiler start   # Begin profiling
/mvs debug profiler stop    # Stop profiling
/mvs debug profiler stats   # Show statistics
```

---

**See Also:** [Configuration](Configuration.md) | [Getting Started](GettingStarted.md) | [Troubleshooting](Troubleshooting.md)

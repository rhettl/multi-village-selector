# Commands Reference

MVS provides in-game commands for configuration and debugging. All commands require **OP level 2**.

## Command Overview

| Command | Description |
|---------|-------------|
| `/mvs help` | Show all commands |
| `/mvs generate` | Generate config from installed mods |
| `/mvs biome` | Current biome info |
| `/mvs biome <id>` | Look up specific biome |
| `/mvs structure biomes <id>` | Show biome rules for structure |
| `/mvs structure test <structure> <biome>` | Test if structure spawns in biome |

**Debug commands** (require `debug_cmd: true` in config):

| Command | Description |
|---------|-------------|
| `/mvs debug mod-scan` | Scan all mods for structures |
| `/mvs debug mod-weights` | Show registry weight analysis |

## /mvs generate

Scans your installed mods and generates a complete config.

**Output:** `local/mvs/multivillageselector.json5`

**What it does:**
1. Scans all registered structures
2. Detects village mods (CTOV, BCA, Towns & Towers, etc.)
3. Generates biome rules from vanilla registry
4. Normalizes weights for equal mod representation

**Workflow:**
```
/mvs generate
→ Review local/mvs/multivillageselector.json5
→ Copy to config/multivillageselector.json5
→ Restart Minecraft
```

## /mvs biome

Shows information about your current biome.

**Example output:**
```
=== Current Biome ===
Location: X: 1234, Y: 72, Z: -5678
Biome: minecraft:plains
Tags: #minecraft:is_plains, #minecraft:has_structure/village_plains
Biome Frequency: 1.0 (100%)
```

### /mvs biome \<biome_id\>

Look up a specific biome without traveling there.

**Example:**
```
/mvs biome terralith:volcanic_peaks
```

## /mvs structure

Commands for inspecting structure configuration.

### /mvs structure biomes \<structure_id\>

Shows which biomes a structure can spawn in and its weights.

**Example:**
```
/mvs structure biomes minecraft:village_plains

=== Structure: minecraft:village_plains ===
Source: MVS Config

Biome Rules:
  #minecraft:is_plains: weight 10
  #minecraft:is_forest: weight 5
```

### /mvs structure test \<structure\> \<biome\>

Tests if a specific structure can spawn in a specific biome.

**Example:**
```
/mvs structure test minecraft:village_desert minecraft:plains

Result: NO
Reason: minecraft:plains is not in biome rules for minecraft:village_desert
```

## Debug Commands

These require `debug_cmd: true` in your config.

### /mvs debug mod-scan

Comprehensive scan of all mods for village structures.

**Output:** `local/mvs/mod-scan-<timestamp>.txt`

**Includes:**
- All registered structures
- Structure set contents
- Mod attribution

### /mvs debug mod-weights

Analyzes registry weights for all village structures.

**Output:** `local/mvs/mod-weights-<timestamp>.txt`

**Includes:**
- Original registry weights
- Normalization factors
- Per-mod weight analysis

## Usage Examples

### Initial Setup

```bash
# 1. Generate config from installed mods
/mvs generate

# 2. Review the generated config
# → Open local/mvs/multivillageselector.json5

# 3. Copy to config folder and restart
```

### Debug Village Spawning

```bash
# 1. Stand in the problem biome
/mvs biome

# 2. Check what structures can spawn here
/mvs structure biomes minecraft:village_plains

# 3. Test a specific structure + biome combo
/mvs structure test minecraft:village_desert minecraft:plains
```

### Verify Biome Frequency

```bash
# Check if biome_frequency is affecting spawn rate
/mvs biome

# Output shows:
# Biome Frequency: 0.3 (30%)
# → Only 30% of spawn attempts in this biome succeed
```

## Quick Reference

```bash
# Help
/mvs help

# Generate config
/mvs generate

# Biome info
/mvs biome
/mvs biome minecraft:plains
/mvs biome terralith:volcanic_peaks

# Structure inspection
/mvs structure biomes minecraft:village_plains
/mvs structure biomes ctov:village_desert
/mvs structure test minecraft:village_desert minecraft:plains

# Debug (requires debug_cmd: true)
/mvs debug mod-scan
/mvs debug mod-weights
```

---

**See Also:**
- [Configuration](Configuration.md) - Config format reference
- [Getting Started](GettingStarted.md) - First-time setup
- [Troubleshooting](Troubleshooting.md) - Common issues

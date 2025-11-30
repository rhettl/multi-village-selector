# Troubleshooting Guide

Common issues and solutions for Multi Village Selector v0.3.0+.

## Table of Contents

- [Known Bugs](#known-bugs)
- [Quick Diagnostics](#quick-diagnostics)
- [Villages Not Spawning](#villages-not-spawning)
- [Wrong Villages in Wrong Biomes](#wrong-villages-in-wrong-biomes)
- [Only One Mod's Villages Spawn](#only-one-mods-villages-spawn)
- [Too Many or Too Few Villages](#too-many-or-too-few-villages)
- [Config Not Loading](#config-not-loading)
- [Debug Mode](#debug-mode)
- [Getting Help](#getting-help)

## Known Bugs

These are confirmed issues with MVS or common mod interactions. Workarounds are provided where available.

### No Villages Near World Spawn

**Symptom:** Villages never spawn within approximately 8-16 chunks (~128-256 blocks) of the world spawn point (0, 0).

**Cause:** This appears to be related to Minecraft's structure placement grid or spawn protection. The exact cause is under investigation.

**Status:** Investigating for v0.4.0 fix.

**Workaround:** Explore further from spawn. Villages will spawn normally once you're beyond ~300 blocks from (0, 0). This is also why testing should be done 1000+ blocks from spawn.

### Better Village Spacing Override

**Symptom:** Village density doesn't match your spacing datapack settings. Villages spawn much less frequently than expected, or only in certain biomes like oceans.

**Cause:** The Better Village mod modifies the `minecraft:villages` structure_set at runtime, overriding the spacing and separation values. Since MVS intercepts structure **selection** but not structure_set **definition**, Better Village's spacing takes precedence.

**Status:** Won't fix in MVS. This is Better Village's intended behavior.

**Workaround:** Disable Better Village's custom spacing in `config/bettervillage_1.properties`:

```properties
boolean.villages.enabled_custom_config=false
```

This lets your spacing datapack (or vanilla defaults) control village density while Better Village still modifies village buildings.

### Spacing/Separation Not Configurable in MVS

**Symptom:** You want to control village density (spacing, separation, spread_type) through MVS config but these options don't exist.

**Cause:** MVS v0.3.0 controls structure **selection** only. Placement grid settings are controlled by datapacks or other mods.

**Status:** Planned for v0.4.0/v1.0. MVS will add a `placement` config section to take full ownership of intercepted structure_sets.

**Workaround:** Use a datapack to control spacing. See [Spacing Guide](SpacingGuide.md) for ready-made datapacks and instructions.

## Quick Diagnostics

Before diving into logs, use these commands:

| Issue | Command | What to Check |
|-------|---------|---------------|
| No villages | `/mvs biome` | Does it show structures for this biome? |
| Wrong villages | `/mvs structure biomes <id>` | Are biome rules correct? |
| Need fresh config | `/mvs generate` | Generate from installed mods |

## Villages Not Spawning

### Enable Debug Logging First

```json5
{
  debug_logging: true
}
```

Then check `logs/latest.log` for MVS entries.

### Common Causes

#### 1. Empty structure_pool

If `structure_pool` is empty, MVS has nothing to select.

**Fix:** Run `/mvs generate` and copy the output to your config.

#### 2. intercept_structure_sets is Empty

MVS won't intercept villages if this is empty.

**Fix:**
```json5
intercept_structure_sets: ["minecraft:villages"]
```

#### 3. No Structures Match Current Biome

If your structures have biome rules that don't match where you are.

**Check with:**
```
/mvs biome
/mvs structure biomes minecraft:village_plains
```

**Fix:** Add `"*:*"` fallback pattern for structures you want everywhere:
```json5
{ structure: "minecraft:village_plains", biomes: {"*:*": 10} }
```

#### 4. Better Village Overriding Spacing

Better Village overrides structure_set spacing at runtime.

**Fix:** Edit `config/bettervillage_1.properties`:
```properties
boolean.villages.enabled_custom_config=false
```

#### 5. Biome Frequency Too Low

If `biome_frequency` is set very low, most spawn attempts fail.

**Check:**
```json5
biome_frequency: {
  "*:*": 0.1  // Only 10% spawn rate!
}
```

**Fix:** Increase to reasonable value (0.5 - 1.0).

### Debug Log Patterns

**Good - MVS working:**
```
[MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
```

**Bad - No attempts:**
If you see no MVS entries, check:
- `intercept_structure_sets` includes `"minecraft:villages"`
- You explored beyond spawn (1000+ blocks)
- It's a new world (old chunks don't regenerate)

## Wrong Villages in Wrong Biomes

### Diagnosis

Stand at the village and run:
```
/mvs biome
```

Check which biome tags it has, then:
```
/mvs structure biomes <structure_id>
```

See if the structure's biome rules match.

### Common Causes

#### 1. Structure Missing Biome Rules

If a structure has no matching biome rule, it won't spawn there.

**Fix:** Add the right biome tag:
```json5
{
  structure: "minecraft:village_desert",
  biomes: {
    "#minecraft:has_structure/village_desert": 10,
    "#minecraft:is_badlands": 5
  }
}
```

#### 2. Universal Fallback Causing Issues

If you use `"*:*"` as fallback, structures spawn everywhere.

**Fix:** Remove `"*:*"` and use specific biome tags:
```json5
// Instead of:
biomes: {"*:*": 10}

// Use:
biomes: {"#minecraft:has_structure/village_plains": 10}
```

#### 3. Modded Biome Not Tagged

Modded biomes may not have standard biome tags.

**Fix:** Use the biome ID directly:
```json5
biomes: {
  "terralith:volcanic_peaks": 10
}
```

## Only One Mod's Villages Spawn

### Diagnosis

Check spawn distribution with debug logging. If one mod dominates, weights are unbalanced.

### Common Causes

#### 1. Unbalanced Weights

```json5
// BAD - CTOV will dominate
{ structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 100} },
{ structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 10} },
```

**Fix:** Balance weights:
```json5
{ structure: "ctov:small/village_plains", biomes: {"#minecraft:has_structure/village_plains": 10} },
{ structure: "minecraft:village_plains", biomes: {"#minecraft:has_structure/village_plains": 10} },
```

#### 2. Pattern Too Broad

A pattern matching too many structures can dominate the pool.

**Check your patterns:** `ctov:*` matches ALL CTOV structures.

#### 3. CTOV Still Spawning Naturally

If you didn't disable CTOV's spawning, you get double villages.

**Fix:** Edit `config/ctov-common.toml`:
```toml
[structures]
    generatesmallVillage = false
    generatemediumVillage = false
    generatelargeVillage = false
```

## Too Many or Too Few Villages

### Village Density Is NOT Controlled by MVS

MVS controls **which** villages spawn, not **how often**.

Village density is controlled by:
- Structure set spacing (datapacks)
- `biome_frequency` (MVS feature for per-biome control)

### For Too Many Villages

1. **Check biome_frequency** - is it 1.0 everywhere?
2. **Check spacing datapack** - see [Spacing Guide](SpacingGuide.md)
3. **Check Better Village** - may override spacing

### For Too Few Villages

1. **Check biome_frequency** - is it too low?
2. **Check structure_pool** - is it empty for some biomes?
3. **Explore more** - spawn zone (0-300 blocks) has no structures

## Config Not Loading

### Symptoms

- Changes don't take effect
- Default behavior instead of your settings

### Solutions

#### 1. Restart Required

Config only loads at startup. `/reload` does NOT reload MVS config.

#### 2. Check JSON5 Syntax

Common errors:

```json5
// WRONG - missing quote
{ structure: minecraft:village_plains }

// CORRECT
{ structure: "minecraft:village_plains" }
```

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

#### 3. Check File Location

Config must be at:
```
<minecraft>/config/multivillageselector.json5
```

#### 4. Delete and Regenerate

1. Delete `config/multivillageselector.json5`
2. Restart Minecraft
3. Run `/mvs generate` for fresh config

## Debug Mode

### Enable Debug Logging

```json5
{
  debug_logging: true,
  debug_cmd: true  // Optional: enables /mvs debug commands
}
```

### What Debug Shows

**Structure Selection:**
```
[MVS] Selecting structure for biome #minecraft:is_plains
[MVS] Candidates: 5 structures, total weight 50
[MVS] Selected: minecraft:village_plains (weight 10)
```

**Generation Result:**
```
[MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
```

**Biome Frequency:**
```
[MVS] Biome frequency roll FAILED for #minecraft:is_ocean (0.30)
```

**Failures:**
```
[MVS] Generation FAILED: minecraft:village_desert - biome mismatch
```

### Debug Commands

With `debug_cmd: true`:

```
/mvs debug mod-scan       # Structure analysis → local/mvs/mod-scan-*.txt
/mvs debug mod-scan all   # Include non-village structures
/mvs debug profiler start # Begin profiling
/mvs debug profiler stop  # Stop profiling
/mvs debug profiler stats # Show performance stats
```

## Getting Help

If you've tried everything:

1. **Enable debug logging**
2. **Create a new world**
3. **Explore 1000+ blocks from spawn**
4. **Save last 200 lines of logs:**
   ```bash
   tail -200 logs/latest.log > mvs-debug.txt
   ```

5. **Open an issue:** [GitHub Issues](https://github.com/RhettL/multi-village-selector/issues)

Include:
- Your config file
- Debug log output
- What you expected vs what happened
- List of village mods installed
- Platform (NeoForge or Fabric)

### Pre-Issue Checklist

- [ ] Debug logging enabled
- [ ] Created NEW world (old chunks don't regenerate)
- [ ] Explored beyond spawn zone (1000+ blocks)
- [ ] CTOV village generation disabled (if using CTOV)
- [ ] Better Village custom config disabled (if using Better Village)
- [ ] Config syntax valid (no JSON errors in logs)
- [ ] Minecraft restarted after config changes
- [ ] Correct Minecraft version (1.21.1)

---

**See Also:**
- [Configuration](Configuration.md) - Complete config reference
- [Mod Compatibility](ModCompatibility.md) - Per-mod setup
- [Commands](Commands.md) - In-game commands
- [Spacing Guide](SpacingGuide.md) - Village density control

# Troubleshooting Guide

Common issues and solutions for Multi Village Selector.

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

### Large Structures Fail Biome Validation

**Symptom:** Large village structures (BCA, CTOV large, Terralith fortified) don't spawn on hilly or uneven terrain even when the biome seems correct.

**Cause:** Vanilla checks the biome at the structure's bounding box center after jigsaw assembly, which can land in a different biome than where MVS selected (chunk center). For large structures, these positions differ significantly.

**Workaround:** Enable `relaxed_biome_validation: true` in your config. See [Configuration](Configuration.md#relaxed_biome_validation) for details.

### Terralith Tall and 3D Biomes

**Symptom:** Structures on tall terrain fail to spawn or spawn with mismatched biomes.

**Cause:** Terralith adds 3D biomes above ground level. Structures on tall terrain may sample a sky biome instead of the expected surface biome.

**Workaround:** Same as above - use `relaxed_biome_validation: true`.

## Quick Diagnostics

Before diving into logs, use these commands:

| Issue | Command | What to Check |
|-------|---------|---------------|
| No villages | `/mvs biome` | Does it show structures for this biome? |
| Wrong villages | `/mvs structure biomes <id>` | Are biome rules correct? |
| Wrong spacing | `/mvs info` | Are placement values what you expect? |
| Find nearest | `/mvs locate <structure>` | Is the structure spawning at all? |
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

#### 4. Biome Frequency Too Low

If `biome_frequency` is set very low, most spawn attempts fail.

**Check:**
```json5
biome_frequency: {
  "*:*": 0.1  // Only 10% spawn rate! AKA 90% of spawns fail BEFORE choosing structure
}
```

**Fix:** Increase to higher value (0.5 - 1.0).

### Debug Log Patterns

**Good - MVS working:**
```
[MVS] Generation SUCCEEDED: minecraft:village_plains at chunk [12, -5]
```

**Bad - No attempts:**
If you see no MVS entries, check:
- `intercept_structure_sets` includes `"minecraft:villages"`
- It's a new world (old chunks don't regenerate)
- Check that `config/multivillageselector.json5` is properly formatted - try [json5.net](https://json5.net/)

## Wrong Villages in Wrong Biomes

### Diagnosis

Stand at the village and run:
```
/mvs biome
```

Check which biome tags it has, then:
```
/mvs structure nearby
```

Click the structure ID to copy it, then:
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

#### 3. Malformed Config File

If the config has syntax errors, MVS may load with an empty or partial structure pool.

**Check:** Validate your config at [json5.net](https://json5.net/)

**Common issues:**
- Missing commas between entries
- Missing quotes around structure IDs
- Unmatched braces `{}`, `[]`

## Too Many or Too Few Villages

Village density is controlled by:
- `placement` config (spacing, separation) - see [Spacing Guide](SpacingGuide.md)
- `biome_frequency` (per-biome spawn rate modifier)

Use `/mvs info` to check current placement values.

### For Too Many Villages

1. **Increase spacing** - Higher spacing = fewer villages
   ```json5
   placement: {
     "minecraft:villages": {
       spacing: 50,    // Up from 34
       separation: 12,
     }
   }
   ```
2. **Lower biome_frequency** - Reduce spawn rate in specific biomes

### For Too Few Villages

1. **Decrease spacing** - Lower spacing = more villages
   ```json5
   placement: {
     "minecraft:villages": {
       spacing: 20,   // Down from 34
       separation: 6,
     }
   }
   ```
2. **Check biome_frequency** - is it too low?
3. **Check structure_pool** - is it empty for some biomes?

**Note:** It's often a good idea to reduce spacing, but **also** reduce frequency, example: 
```json5
{
   biome_frequency: {
      "*:*": 0.5, // reduces number of spawning villages
   },
   placement: {
     "minecraft:villages": {
       spacing: 20, // but puts them closer together
       separation: 6, // while still keeping them apart
       spreadType: "triangular", // and encouraging them away from each other
     }
   }
}
```

### Villages Running Together / Overlapping

Villages spawning too close and blending into each other? This is a **separation** issue, not spacing.

1. **Increase separation** - Creates larger buffer zones between cells
   ```json5
   placement: {
     "minecraft:villages": {
       spacing: 34,
       separation: 12,  // Up from 8
     }
   }
   ```
2. **Use center-biased spread** - Keeps villages away from cell edges
   ```json5
   placement: {
     "minecraft:villages": {
       spacing: 34,
       separation: 8,
       spreadType: "triangular",  // or "gaussian" for even tighter clustering
     }
   }
   ```

See [Spread Types](SpreadTypes.md) for visual diagrams.

## Config Not Loading

### Symptoms

- Changes don't take effect
- Default behavior instead of your settings

### Solutions

#### 1. Reload the Config

Use `/mvs config reload` to reload the config without restarting. Note: vanilla `/reload` does NOT reload MVS config.

#### 2. Check JSON5 Syntax

Validate your config at [json5.net](https://json5.net/). Common errors:

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
4. Move from `local/mvs/multivillageselector.json5` to `config/multivillageselector.json5`
5. Restart Minecraft

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
3. **Explore and generate some chunks**
4. **Upload your log** to [mclo.gs](https://mclo.gs/) (don't paste logs directly in issues)
5. **Open an issue:** [GitHub Issues](https://github.com/RhettL/multi-village-selector/issues)

Include:
- Link to your log on mclo.gs
- Your config file
- What you expected vs what happened
- List of village mods installed
- Platform (NeoForge or Fabric)
- Screenshot if helpful 

### Pre-Issue Checklist

- [ ] Debug logging enabled
- [ ] Created NEW world (old chunks don't regenerate)
- [ ] Explored beyond spawn
- [ ] Config syntax valid (no JSON errors in logs)
- [ ] Ran `/mvs config reload` or restarted Minecraft
- [ ] Ran `/mvs info` to verify placement values
- [ ] Correct Minecraft version (1.21.1)

---

**See Also:**
- [Configuration](Configuration.md) - Complete config reference
- [Mod Compatibility](ModCompatibility.md) - Per-mod setup
- [Commands](Commands.md) - In-game commands
- [Spacing Guide](SpacingGuide.md) - Village density control

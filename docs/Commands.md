# MVS Commands Reference

Multi Village Selector provides several in-game commands for debugging and inspecting your configuration. All commands require **OP permission level 2** (server operator).

## Table of Contents
- [Command Overview](#command-overview)
- [Configuration Commands](#configuration-commands)
- [Biome Commands](#biome-commands)
- [Pool Commands](#pool-commands)
- [Usage Examples](#usage-examples)

---

## Command Overview

| Command | Description |
|---------|-------------|
| `/mvs` or `/mvs help` | Show help menu with all commands |
| `/mvs generate` | Generate smart config from installed mods |
| `/mvs biome` | Show your current biome, location, and category |
| `/mvs biome <biome_id>` | Look up category for a specific biome |
| `/mvs biome list` | List all biomes grouped by category |
| `/mvs pools` | List all configured pool categories |
| `/mvs pools <category>` | Show structures in a pool with weights |

**Permission Required:** All commands require OP level 2 (`/op <username>`)

---

## Configuration Commands

### `/mvs generate` - Generate Smart Config

Automatically generates a complete MVS configuration file based on your installed mods and structures.

**What It Does:**
1. **Scans all registered structures** in your modpack
2. **Detects installed village mods** (BCA, CTOV, Towns & Towers, Terralith, etc.)
3. **Categorizes villages** by biome type based on their names
4. **Identifies uncategorized biomes** that would use the DEFAULT pool
5. **Generates a ready-to-use config** with smart defaults

**Output Location:**
- File: `local/mvs/multivillageselector.json5`
- The chat will show a **clickable link** to open the file directly

**Generated Config Includes:**
- **`replace_of`**: Vanilla villages + BCA villages (if BCA is installed)
- **`prevent_spawn`**: All modded villages (CTOV, Towns & Towers, etc.)
- **`replace_with`**: Villages organized by biome category with equal weights
- **`biome_category_overrides`**: Commented suggestions for uncategorized biomes

**Example Output:**
```
=== Generating Smart Config ===
Mods detected: 4
Villages found: 47
Uncategorized biomes: 12
File: local/mvs/multivillageselector.json5

⚠️  Review and copy to config/multivillageselector.json5
⚠️  Restart Minecraft to apply changes
```

**Use Cases:**
- **Initial setup**: Quickly create a working config for your modpack
- **After adding mods**: Regenerate config when you add new village mods
- **Troubleshooting**: Generate a fresh config to compare with your current one
- **Learning**: See how MVS categorizes your installed villages

**Workflow:**
1. Run `/mvs generate` in-game
2. **Click the file link** in chat (or navigate to `local/mvs/`)
3. **Review the generated config** - adjust weights and categories as desired
4. **Copy to `config/multivillageselector.json5`** (overwrites your current config)
5. **Restart Minecraft** for changes to take effect

**Important Notes:**
- This generates a **starting point** - you should review and adjust weights
- Biomes are categorized by name matching (may need manual overrides)
- All modded villages get equal weight (10) - adjust based on preference
- Uncategorized biomes are commented out - uncomment and set categories manually

---

## Biome Commands

### `/mvs biome list` - List All Biomes by Category

Lists all registered biomes grouped by their MVS category. Shows which biomes are explicitly categorized and which fall back to DEFAULT.

**Output Structure:**
```
=== All Biomes by Category ===

PLAINS (15)
  minecraft:plains
  minecraft:meadow
  minecraft:forest
  ...

DESERT (8)
  minecraft:desert
  terralith:volcanic_peaks
  ...

UNCATEGORIZED (4) - uses temperature fallback → DEFAULT
  modname:custom_biome_1
  modname:custom_biome_2
  ...

Total: 127 biomes, 9 categories
```

**Use Cases:**
- **Initial setup**: Identify which modded biomes need category overrides
- **Verify config**: Check that your `biome_category_overrides` are working
- **Find gaps**: See which biomes would use the DEFAULT pool
- **Plan config**: Understand your modpack's biome distribution

**Understanding the Output:**
- **Categorized biomes**: Have explicit name matches or config overrides
- **UNCATEGORIZED**: Biomes that don't match any pattern
  - These use temperature-based fallback → DEFAULT pool
  - Add them to `biome_category_overrides` if you want specific village types

**Example Workflow:**
1. Run `/mvs biome list` to see all biomes
2. Check UNCATEGORIZED section
3. Add desired biomes to `biome_category_overrides`:
   ```json5
   "biome_category_overrides": {
     "biomesoplenty:lavender_field": "plains",
     "terralith:yellowstone": "savanna"
   }
   ```
4. Restart and verify with `/mvs biome list` again

---

## Biome Commands

### `/mvs biome` - Current Biome Info

Shows information about the biome you're currently standing in.

**Output:**
- **Location:** Your X, Y, Z coordinates
- **Biome:** Full biome ID (e.g., `minecraft:plains`, `terralith:volcanic_peaks`)
- **Temperature:** Biome's base temperature value
- **Category:** MVS category this biome maps to (e.g., `plains`, `desert`, `snowy`)
- **Source:** Whether category is from config override or automatic detection
- **Available Structures:** Number of structures configured for this category

**Example Output:**
```
=== Current Biome ===
Location: X: 1234, Y: 72, Z: -5678
Biome: minecraft:plains
Temperature: 0.80
Category: plains
Available Structures: 5
```

**Use Case:**
- Walk around your world to see how different biomes are categorized
- Debug why certain villages are spawning in specific locations
- Verify your `biome_category_overrides` are working correctly

---

### `/mvs biome <biome_id>` - Lookup Specific Biome

Look up which category a specific biome maps to without traveling there.

**Arguments:**
- `<biome_id>` - Full biome ID in format `namespace:biome` (e.g., `minecraft:plains`)

**Example Output:**
```
=== MVS Biome Info ===
Biome: minecraft:deep_dark
Category: dark_forest
Source: Config Override
Available Structures: 3
```

**Use Cases:**
- Check how a modded biome will be categorized before visiting it
- Verify your config overrides are applied correctly
- Plan which structures will spawn in specific biomes

**Examples:**
```
/mvs biome minecraft:plains
/mvs biome terralith:frozen_cliffs
/mvs biome biomesoplenty:tropical_rainforest
/mvs biome minecraft:deep_dark
```

---

## Pool Commands

### `/mvs pools` - List All Categories

Shows all configured pool categories and how many structures each contains.

**Example Output:**
```
=== MVS Pool Categories ===
Total Categories: 8

  • plains (5 structures)
  • desert (4 structures)
  • snowy (6 structures)
  • taiga (4 structures)
  • jungle (3 structures)
  • swamp (2 structures)
  • ocean (1 structures)
  • DEFAULT (3 structures)

Use /mvs pools <category> to see details
```

**Use Cases:**
- Get an overview of your entire configuration
- See which categories have structures configured
- Identify missing or empty categories

---

### `/mvs pools <category>` - Pool Details

Shows all structures in a specific category with their weights and spawn percentages.

**Arguments:**
- `<category>` - Pool category name (e.g., `plains`, `desert`, `DEFAULT`)

**Example Output:**
```
=== MVS Pool: plains ===
Total Weight: 100

  [20] (20.0%) minecraft:village_plains
  [30] (30.0%) ctov:small/village_plains
  [40] (40.0%) bca:village/default_large
  [10] (10.0%) (empty - no spawn)
```

**Interpreting the Output:**
- `[20]` - Weight value from config
- `(20.0%)` - Actual spawn probability
- `(empty - no spawn)` - Weighted chance for no village to spawn

**Use Cases:**
- Verify structure weights are configured correctly
- Check spawn percentages to balance village variety
- Debug why certain villages spawn more frequently than others
- Identify if you have empty (no-spawn) entries configured

**Examples:**
```
/mvs pools plains
/mvs pools desert
/mvs pools DEFAULT
/mvs pools snowy
```

---

## Usage Examples

### Example 1: Debug Village Spawning

You find a village in a snowy biome but it's not the type you expected.

1. **Stand near the village** and run:
   ```
   /mvs biome
   ```

2. **Check the output:**
   ```
   Location: X: 1000, Y: 70, Z: 2000
   Biome: minecraft:snowy_plains
   Category: snowy
   ```

3. **Check what villages can spawn here:**
   ```
   /mvs pools snowy
   ```

4. **Review the pool:**
   ```
   [50] (50.0%) minecraft:village_snowy
   [30] (30.0%) ctov:small/village_snowy
   [20] (20.0%) bca:village/default_small
   ```

Now you know the village had a 50% chance to be vanilla, 30% CTOV, and 20% BCA.

---

### Example 2: Configure Modded Biomes

You added Terralith and want to check how its biomes are categorized.

1. **Look up a Terralith biome:**
   ```
   /mvs biome terralith:volcanic_peaks
   ```

2. **Check the output:**
   ```
   Category: desert
   Source: Name Matching / Temperature
   ```

3. **If you want it in a different category,** add to your config:
   ```json5
   "biome_category_overrides": {
     "terralith:volcanic_peaks": "snowy"
   }
   ```

4. **Restart and verify:**
   ```
   /mvs biome terralith:volcanic_peaks
   ```
   Should now show `Category: snowy` with `Source: Config Override`

---

### Example 3: Balance Village Variety

You want more variety in plains villages.

1. **Check current plains pool:**
   ```
   /mvs pools plains
   ```

2. **Current output:**
   ```
   [80] (80.0%) minecraft:village_plains
   [20] (20.0%) ctov:small/village_plains
   ```

3. **Update your config** to balance the weights:
   ```json5
   "plains": [
     { "pattern": "minecraft:village_plains", "weight": 40 },
     { "pattern": "ctov:*/village_plains", "weight": 30 },
     { "pattern": "towns_and_towers:village_*", "weight": 30 }
   ]
   ```

4. **Restart and verify:**
   ```
   /mvs pools plains
   ```
   Should now show more balanced percentages (40%, 30%, 30%)

---

### Example 4: Find All Available Pools

You want to know what categories are available.

```
/mvs pools
```

Output shows all categories:
- `plains`, `desert`, `snowy`, `taiga`, `jungle`, `swamp`, `ocean`, `DEFAULT`

Use this to plan your configuration and ensure all biome types have villages.

---

## Permission Levels

MVS commands use Minecraft's standard OP permission system:

| Level | Description | Can Use MVS? |
|-------|-------------|--------------|
| 0 | Regular player | ❌ No |
| 1 | Bypass spawn protection | ❌ No |
| 2 | Use commands, command blocks | ✅ Yes |
| 3 | Game moderator | ✅ Yes |
| 4 | Server owner | ✅ Yes |

To grant permission:
```
/op <username>
```

To set a specific level (2 is enough for MVS):
```
/op <username>
```
Then edit `ops.json` to set `"level": 2`

---

## Troubleshooting

### "This command can only be used by a player"

- The `/mvs biome` command (without arguments) requires you to be a player in the world
- Use `/mvs biome <biome_id>` from console/command blocks instead

### "Unknown category: xyz"

- The category name doesn't exist in your configuration
- Use `/mvs pools` to see all available categories
- Check spelling (case-sensitive)

### Commands not working

1. **Check your OP level:**
   ```
   /op <your_username>
   ```

2. **Verify MVS is loaded:**
   - Check `latest.log` for "MVS commands registered"

3. **Try the help command:**
   ```
   /mvs help
   ```

### Empty pools showing

If `/mvs pools <category>` shows no structures:
- You may not have any structures matching your patterns in that category
- Check your config's `replace_with` section
- Run structure discovery by restarting the server

---

## Related Documentation

- [Configuration Guide](Configuration.md) - Learn how to configure pools and overrides
- [Mod Compatibility](ModCompatibility.md) - Setup guides for different village mods
- [Troubleshooting](Troubleshooting.md) - Common issues and solutions

---

## Command Reference Quick Sheet

**Quick Copy-Paste Commands:**
```
/mvs help
/mvs generate
/mvs biome
/mvs biome list
/mvs biome minecraft:plains
/mvs biome terralith:volcanic_peaks
/mvs pools
/mvs pools plains
/mvs pools desert
/mvs pools DEFAULT
```

**Common Debugging Workflows:**

**Initial Setup:**
1. `/mvs generate` - Generate starter config
2. Review and copy to `config/multivillageselector.json5`
3. `/mvs biome list` - Check which biomes need overrides
4. Edit config with desired overrides
5. Restart Minecraft

**Village Spawning Issues:**
1. `/mvs biome` - Check current location and category
2. `/mvs pools <category>` - Review spawn chances
3. Edit config weights if needed
4. Restart Minecraft
5. `/mvs pools <category>` - Verify changes

**Modded Biome Setup:**
1. `/mvs biome list` - Find uncategorized biomes
2. `/mvs biome <mod:biome>` - Check specific biome categorization
3. Add to `biome_category_overrides` if needed
4. Restart Minecraft
5. `/mvs biome <mod:biome>` - Verify override worked

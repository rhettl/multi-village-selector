# Multi Village Selector - Design Philosophy & Scope

This document explains what MVS does, what problems it solves, and what it intentionally does NOT do.

## The Problem

When you install multiple village mods (CTOV, BCA, Terralith, Towns & Towers, etc.), they compete for spawn locations:

1. **Override conflicts** - Only one mod's villages actually spawn; others are silently ignored
2. **Invisible weights** - You can't see or adjust how often each village type spawns
3. **No biome control** - You can't say "CTOV villages in plains, BCA in forests"
4. **Black box behavior** - You don't know what will spawn or why
5. **Knowledge barrier** - Fixing it requires datapack expertise

## Who MVS Is For

MVS is designed for **power users** who:

- Can install mods and edit JSON config files
- Want control over village variety without learning datapacks
- Are building modpacks (personal or for distribution)
- Want predictable, reproducible village generation

**MVS assumes you're comfortable editing config files.** It's not a GUI mod, and it won't hold your hand through basic modding concepts.

## What MVS Controls

### Structure Selection
Which village structures can spawn in your world. You define a pool of structures, and MVS selects from them.

### Relative Weights
How often each structure spawns relative to others. Weight 20 spawns twice as often as weight 10.

### Biome Filtering
Where each structure can spawn. Desert villages in deserts, snowy villages in snow - or override this completely.

### Overall Frequency
How often villages spawn at all in a biome. Reduce spawn rate in oceans, increase in plains.

## What MVS Does NOT Control

### Spacing and Separation
Village grid spacing is controlled at the structure_set level via datapacks, not by MVS. See [Spacing Guide](SpacingGuide.md) for how to adjust this.

### Jigsaw Piece Replacement
MVS intercepts **structure selection** (which village type spawns), not **jigsaw piece assembly** (which buildings appear inside villages).

Mods that replace individual village pieces work at a different level:
- **Better Villages** - Replaces vanilla village jigsaw pieces with enhanced versions
- **Luki's Grand Capitals** - Replaces/extends vanilla village pieces

These mods will still apply their changes to whatever village MVS selects. This is usually fine - MVS picks the village type, then the jigsaw replacer modifies its buildings. However, MVS cannot control *which* piece replacements are used.

### Structure Content
What's inside villages (buildings, loot, villagers) is controlled by the village mods themselves.

### Custom Worldgen Systems
Mods with their own generation systems (MineColonies, Millénaire) bypass vanilla structure placement. MVS cannot intercept these.

### Other Structures
MVS focuses on villages. Temples, monuments, and other structures are out of scope.

## Design Principles

### Deterministic
Same config + same seed + same biomes = same results. No hidden RNG or "magic" behavior. What you configure is what you get.

### Transparent
- The config file documents itself
- Debug logging shows every decision
- Commands reveal why structures spawn where they do

### Discoverable
- `/mvs generate` scans your mods automatically
- `/mvs biome` shows what spawns in current location
- `/mvs structure` explains biome rules for any structure

## Current Limitations

### Overworld Villages Only
MVS currently only handles Overworld village generation. Nether and End structures are not supported.

**Why?** Supporting all dimensions would transform MVS from a focused village tool into a general structure framework - a different project entirely.

### Jigsaw-Based Structures Only
MVS intercepts vanilla jigsaw structure placement. Mods that use custom worldgen (not jigsaw) can't be controlled.

### Single Player Focus
MVS is tested primarily in single-player. Multiplayer should work, but edge cases aren't extensively tested.

## Future Possibilities

Features under consideration for future versions:

- **Spacing control** - Configure village grid spacing in MVS config
- **Additional structure types** - Pillager outposts, ocean ruins
- **Modded biome inference** - Auto-detect desert-like modded biomes
- **Hot reload** - Apply config changes without restart

These are possibilities, not promises. MVS will stay focused on its core mission: **bringing village variety to your world with full user control**.

---

**See Also:**
- [Getting Started](GettingStarted.md) - Installation and setup
- [Configuration](Configuration.md) - Complete config reference
- [Troubleshooting](Troubleshooting.md) - Common issues

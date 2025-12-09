# Mod Compatibility Guide

This guide explains how MVS works with popular village mods.

## Just Works

These mods work with MVS automatically. Run `/mvs generate` to create a config that includes their structures.

- Badland Village 3.4
- Cherry Village 1.0.0
- Cherry Villages 1.1.2
- Cobblemon Additions (BCA) 4.1.6
- ChoiceTheorem's Overhauled Village (CTOV) 3.6.0b
- Epic Villages 1.1.0
- Hobbit Hill Village 0.0.2
- Japanese Offering Shrines 1.0
- Katter's Structures (Village Only) 2.1
- Lio's Overhauled Villages 0.0.9
- Moogs Missing Villages 2.0.0
- Moogs Structures 1.1.0
- Moogs Voyager Structures 5.0.2 (<== also "mvs")
- Qrafty's Bamboo Villages 2.2
- Qrafty's Bunkers 2.2
- Qrafty's Halloween Villages 2.2
- Qrafty's Japanese Villages 2.2
- Qrafty's Jungle Villages 2.2
- Qrafty's Mangrove Villages 2.2
- Qrafty's Mushroom Villages 2.2
- Repurposed Structures 7.5.17
- Ribbits 4.1.4
- Sky Villages 1.0.6
- Spiral Tower Village 0.0.2
- Terralith 2.5.8
- Tidal Towns 1.3.4
- Towns & Towers 1.13.7
- Trek B0.5.1.1
- Underground Village 1.5.4
- Vanilla Sky Villages 1.0.4
- Villages and Pillages 1.0.3

No special configuration needed. MVS intercepts their spawn attempts and applies your weights and biome rules.

## Jigsaw Mods

These mods replace individual *pieces* inside villages (buildings, paths, etc.), not the village selection itself. They operate at a different level than MVS.

**Tested:**
- Luki's Grand Capitals 1.1.2 (NeoForge & Fabric)
- Better Village 3.3.1 (Fabric)

**How it works:**
These mods usually only replace vanilla village parts, so this is the flow

1. A village should spawn somewhere
2. MVS selects *which village type* spawns (vanilla, CTOV, BCA, etc.)
3. If a vanilla village is chosen, the jigsaw mod modifies *what buildings* appear in it

So, if you make vanilla have only 5% chance of spawning, Better Villages will only show in 5% of villages

MVS is compatible with jigsaw mods. However, **use only one jigsaw mod** - they conflict with each other.

## Mods That Change Placement

Some mods (like Better Village) override village spacing settings at runtime. As of v0.4.0, MVS's `placement` config takes precedence over these changes, so this is no longer a concern.

If you want to control village density, use MVS's `placement` config. See [Configuration](Configuration.md#placement) and [Spacing Guide](SpacingGuide.md) for details.

If you think the in-game spacing is wrong, try using `/mvs info` to inspect the present spacing.

## Non-Village Mods Tested

These mods have been tested alongside MVS without issues:

- **Concurrent Chunk Management Engine (C2ME)** - Concurrent/faster chunk generation
- **Explorer's Compass** - Survival player structure location
- **Nature's Compass** - Biome location
- **Chunky** - Pre-generation

## Known Issues

### Large Structures & Biome Validation

Large village structures (BCA, CTOV large, Terralith fortified) may fail vanilla's biome validation on hilly or uneven terrain. This happens because vanilla checks the biome at the structure's bounding box center, which can land in a different biome than where MVS selected.

**Solution:** Enable `relaxed_biome_validation: true` in your config. See [Configuration](Configuration.md#relaxed_biome_validation) for details.

Enabling this will allow more villages to spawn, ignoring an annoying vanilla check, but will likely cause some odd spawns when combined with Terralith or Tectonic (tall biomes).

### Terralith Sky Biomes

Terralith adds 3D biomes above ground level. Structures on tall terrain may sample a sky biome instead of the expected surface biome, causing validation failures.

**Solution:** Same as above - use `relaxed_biome_validation: true`.

---

**See Also:**
- [Configuration](Configuration.md) - Full config reference
- [Spacing Guide](SpacingGuide.md) - Village density control
- [Troubleshooting](Troubleshooting.md) - Common issues

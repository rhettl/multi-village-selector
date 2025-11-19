# Mod Compatibility Guide

This guide documents how different village mods interact with MVS and how to configure them for optimal compatibility.

## Mod Categories

Village mods fall into three main categories based on how they generate villages:

### Category 1: Standard Village Mods (Use `prevent_spawn`)

These mods add villages to vanilla structure sets without overriding them. They spawn **in addition** to vanilla villages.

#### How MVS should handle them

- Add to `prevent_spawn` - blocks their natural spawning
- Add to `replace_with` pools - allows MVS to select them as replacements

#### Mods in this category

- **CTOV** (ChoiceTheorem's Overhauled Village) - Adds to vanilla structure sets with Lithostitched
- **Towns & Towers** - Standard structure placement
- **Villages & Pillages** - Standard structure placement
- **Joshie's Villages** - Standard structure placement

#### Configuration
```json5
prevent_spawn: [
  "ctov:large/*",
  "ctov:medium/*",
  "ctov:small/*",
  "towns_and_towers:village_*",
  // ... etc
],

replace_with: {
  plains: [
    { pattern: "ctov:*/village_plains", weight: 30 },
    { pattern: "towns_and_towers:village_*plains", weight: 30 },
    // ... etc
  ]
}
```

### Category 2: Structure Set Overrides (Use `replace_of`)

These mods completely replace vanilla village structure sets with their own villages.

#### How MVS should handle them

- Add to `replace_of` - intercepts their spawn attempts
- Do NOT add to `prevent_spawn` - we want them to spawn so we can intercept
- Add to `replace_with` pools - allows MVS to select them

#### Mods in this category

- **BCA** (Cobblemon Additions) - Overrides `minecraft:worldgen/structure_set/villages.json`

#### Configuration
```json5
replace_of: [
  "minecraft:village_plains",
  "minecraft:village_desert",
  "minecraft:village_savanna",
  "minecraft:village_snowy",
  "minecraft:village_taiga",
  // BCA villages (they replace vanilla)
  "bca:village/default_small",
  "bca:village/default_mid",
  "bca:village/default_large",
  "bca:village/dark_mid",
  "bca:village/dark_small",
],

replace_with: {
  plains: [
    { pattern: "bca:village/default_*", weight: 25 },
    // ... etc
  ]
}
```

### Category 3: Standalone Structures (Optional)

These are village-like structures that spawn independently and may or may not need handling.

#### Examples

- `bca:village/witch_hut` - Special structure, not a traditional village
- `towns_and_towers:exclusives/village_piglin` - Nether structure
- Pillager outposts, witch huts, etc.

#### Configuration

Usually left alone (not in `prevent_spawn` or `replace_of`)

---

## Mod-Specific Configuration

### CTOV (ChoiceTheorem's Overhauled Village)

#### Type
Standard Village Mod (Category 1)

#### Requirement
Disable CTOV village generation in their config

#### Config file
`config/ctov-common.toml`
```toml
[structures]
    generatesmallVillage = false
    generatemediumVillage = false
    generatelargeVillage = false
```

#### MVS Configuration
```json5
prevent_spawn: [
  "ctov:large/*",
  "ctov:medium/*",
  "ctov:small/*",
],

replace_with: {
  plains: [
    { pattern: "ctov:*/village_plains", weight: 30 },
    { pattern: "ctov:*/village_plains_fortified", weight: 25 },
    { pattern: "ctov:*/village_mountain", weight: 20 },
  ],
  desert: [
    { pattern: "ctov:*/village_desert", weight: 30 },
    { pattern: "ctov:*/village_desert_fortified", weight: 25 },
  ],
  // ... etc for each biome
}
```

#### Why
CTOV uses Lithostitched to add villages to vanilla structure sets. Without disabling CTOV spawning, you'll get both CTOV villages naturally AND MVS selections, resulting in double density.

---

### BCA (Cobblemon Additions)

**Version:** 4.0.1 (current behavior documented below)

#### Type
Structure Set Override (Category 2)

#### Requirement
**No configuration needed** - BCA villages spawn by default and MVS automatically intercepts them

#### Configuration Note for BCA 4.0.1
BCA 4.0.1 has a minor configuration bug that's being investigated:
- CristelLib may auto-generate configs in `config/cristellib/cobblemon_additions/*` but BCA doesn't currently read these configs.
- A more comprehensive config system is planned for future releases
- Check BCA release notes for updates

**MVS handles this automatically** - We've added special casing for BCA villages so everything works correctly regardless 
of the config situation.

#### MVS Configuration

**IMPORTANT:** Use correct pattern format with `village/` path:

```json5
replace_of: [
  "minecraft:village_plains",
  "minecraft:village_desert",
  "minecraft:village_savanna",
  "minecraft:village_snowy",
  "minecraft:village_taiga",
  // BCA villages - note the village/ path!
  "bca:village/default_*",
  "bca:village/dark_*",
  // Optionally add witch_hut if you want MVS to handle it
  // "bca:village/witch_hut",
],

prevent_spawn: [],  // Do NOT add BCA here - use replace_of

replace_with: {
  plains: [
    { pattern: "bca:village/default_*", weight: 25 },
    // Or list individually with mod-intended weights:
    // { structure: "bca:village/default_small", weight: 16 },
    // { structure: "bca:village/default_mid", weight: 11 },
    // { structure: "bca:village/default_large", weight: 2 },
  ],
  dark_forest: [
    { pattern: "bca:village/dark_*", weight: 50 },
    // Or with mod-intended weights:
    // { structure: "bca:village/dark_small", weight: 16 },
    // { structure: "bca:village/dark_mid", weight: 11 },
  ],
  swamp: [
    { pattern: "bca:village/dark_*", weight: 50 },
  ],
  // Add to other biomes as desired
}
```

#### BCA Intended Weights

From BCA's structure_set, these are the mod's intended spawn rates:

- `default_small`: weight 16 (28%)
- `default_mid`: weight 11 (19%)
- `default_large`: weight 2 (3%)
- `dark_small`: weight 16 (28%)
- `dark_mid`: weight 11 (19%)
- `witch_hut`: weight 1 (2%)

Small variants are common, mid variants are frequent, large variants are rare.

#### Why This Configuration

BCA completely **replaces the vanilla structure set** via datapack:
- Overwrites `minecraft/worldgen/structure_set/villages.json`
- Contains only BCA villages (no vanilla entries)
- Uses biome tags to control where DEFAULT vs DARK villages spawn
- MVS intercepts BCA spawn attempts and replaces with variety from all mods

**Key point:** BCA doesn't spawn "in addition" to vanilla - it IS the vanilla spawn system when installed.

#### Pattern Format

**Important detail:** BCA structure names include a `village/` subdirectory path:

```json5
// These patterns won't match BCA structures:
"bca:default_*"
"bca:dark_*"

// Use these instead:
"bca:village/default_*"
"bca:village/dark_*"
```

The `/mvs generate` command automatically uses the correct format when BCA is installed.

#### Future Versions

BCA is working on enhancing their config system. Future releases may include:
- User-editable biome tag files
- More customization options for village spawning
- Additional configuration features

**MVS will continue to work regardless** - We intercept at the structure spawn level, so changes to BCA's config system won't affect MVS compatibility.

---

### Towns & Towers

#### Type
Standard Village Mod (Category 1)

#### Requirement
None (works out of the box)

#### MVS Configuration
```json5
prevent_spawn: [
  "towns_and_towers:village_*",
  "towns_and_towers:exclusives/village_*",
],

replace_with: {
  plains: [
    { pattern: "towns_and_towers:village_*plains", weight: 30 },
    { pattern: "towns_and_towers:village_meadow", weight: 30 },
    { pattern: "towns_and_towers:village_*forest", weight: 30 },
    { pattern: "towns_and_towers:exclusives/village_classic", weight: 25 },
    { pattern: "towns_and_towers:exclusives/village_rustic", weight: 25 },
    { pattern: "towns_and_towers:exclusives/village_tudor", weight: 25 },
    { pattern: "towns_and_towers:exclusives/village_swedish", weight: 25 },
  ],
  // ... etc
}
```

#### Note
Towns & Towers has many exclusive villages. Check structure registry with `/locate structure` to discover all available villages.

---

### Terralith

#### Type
Standard Village Mod (Category 1)

#### Requirement
None

#### MVS Configuration
```json5
prevent_spawn: [
  "terralith:fortified_*_village",
],

replace_with: {
  plains: [
    { structure: "terralith:fortified_village", weight: 20 },
  ],
  desert: [
    { structure: "terralith:fortified_desert_village", weight: 25 },
  ],
}
```

#### Note
Terralith has only a few fortified villages. They're special variants and should have lower weights to maintain rarity.

---

### Villages & Pillages

#### Type
Standard Village Mod (Category 1)

#### Requirement
Check individual structure spawning rules

#### MVS Configuration
```json5
prevent_spawn: [
  // Add specific villages as needed
],

replace_with: {
  // Add to appropriate biome categories
}
```

#### Note
Villages & Pillages has various village types. Test which ones spawn naturally and add to `prevent_spawn` as needed.

---

## Quick Reference Matrix

| Mod | Category | Disable in Mod Config? | Add to replace_of? | Add to prevent_spawn? |
|-----|----------|----------------------|-------------------|---------------------|
| CTOV | Standard | ✅ Yes | ❌ No | ✅ Yes |
| BCA (4.0.1) | Override | ❌ No (MVS handles it) | ✅ Yes | ❌ No |
| Towns & Towers | Standard | ❌ No config needed | ❌ No | ✅ Yes |
| Terralith | Standard | ❌ No config needed | ❌ No | ✅ Yes |
| Villages & Pillages | Standard | ❌ No config needed | ❌ No | ✅ Yes |

---

## Testing for Compatibility

To determine if a new village mod is compatible with MVS:

1. **Install the mod** with MVS
2. **Enable debug logging** in MVS config: `debug_logging: true`
3. **Create a new world** and explore
4. **Check logs** for structure attempts:
   ```
   [MVS-DEBUG] Structure attempt: modname:village_type at chunk [X, Z]
   ```

If you see the mod's villages attempting to spawn:
- Add them to `prevent_spawn` if they're spawning naturally
- Add them to `replace_with` pools for selection

If you DON'T see them attempting:
- They might not be spawning (check mod config)
- They might be using a different spawn system (data packs, custom code)

---

## Known Issues

### CTOV + BCA Together

When using both CTOV and BCA:
- Disable CTOV village spawning (as above)
- Keep BCA enabled
- Add both to appropriate config sections
- BCA will control spawn locations, MVS will provide variety

### Lithostitched Warnings

You may see warnings like:
```
[lithostitched/]: Couldn't find template pool reference: ctov:village/waystone/sand
```

These are harmless - Lithostitched is looking for optional structure pieces that may not exist in all villages.

---

## Future Compatibility

**BCA is updating their village system** in an upcoming release. When that happens:
- Check if they still override structure sets
- Update MVS configuration accordingly
- See release notes for migration guide

---

## Contributing Compatibility Info

Found a compatible village mod? Please contribute!

1. Test the mod with MVS
2. Determine its category (Standard vs Override)
3. Document required configuration
4. Submit a PR or open an issue with your findings

Together we can build a comprehensive compatibility database!

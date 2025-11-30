# Multi Village Selector (MVS)

**Minecraft 1.21.1 | NeoForge & Fabric | MIT License**

Multi Village Selector gives you control over village spawning in Minecraft. Instead of each village mod fighting for spawn slots, MVS intercepts village generation and selects from **all your installed village mods** using configurable weights.

## The Problem MVS Solves

When you install multiple village mods (Cobblemon Additions, ChoiceTheorem's Overhauled Village, Towns & Towers, etc.), they compete for the same spawn locations. Some mods override others, some never spawn, and you have no control over the mix. MVS fixes this by:

- **Intercepting** vanilla village spawns before any mod processes them
- **Selecting** from all configured village structures using weighted random selection
- **Respecting** biome rules so desert villages spawn in deserts, snowy villages in snow, etc.

## Download

- **Modrinth**: [Multi Village Selector](https://modrinth.com/mod/multi-village-selector)
- **GitHub Releases**: [Latest Release](https://github.com/RhettL/multi-village-selector/releases)

**Requirements:**
- Minecraft 1.21.1
- NeoForge 21.1.80+ **or** Fabric Loader 0.16.0+ with Fabric API
- [Architectury API](https://modrinth.com/mod/architectury-api) (required for both platforms)

## Quick Start

1. **Install** MVS and Architectury API in your `mods/` folder
2. **Launch** Minecraft and load a world
3. **Run** `/mvs generate` in-game to scan your installed mods
4. **Review** the generated config at `local/mvs/multivillageselector.json5`
5. **Move** the config to `config/multivillageselector.json5`
6. **Restart** Minecraft to apply changes

That's it! Villages from all your mods will now spawn with weighted representation -- but be sure to look over the weights, the generator makes some guesses and isn't perfect.

For detailed setup instructions, see the **[Getting Started Guide](https://github.com/RhettL/multi-village-selector/blob/master/docs/GettingStarted.md)**.

## Configuration

MVS uses a JSON5 config file with three main sections:

```json5
{
  // Which structure sets MVS controls (usually just minecraft:villages)
  intercept_structure_sets: ["minecraft:villages"],

  // Your village structures with per-biome weights
  structure_pool: [
    { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 25} },
    { structure: "bca:village/default_mid", biomes: {"#bca:villages": 29} },  // Cobblemon Additions
    // ... more structures
  ],

  // Optional: Reduce spawn rate in specific biomes
  biome_frequency: {
    "#minecraft:is_ocean": 0.3,  // Only 30% of ocean spawn attempts proceed
  }
}
```

See the **[Configuration Guide](https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md)** for complete documentation.

## Commands

```bash
/mvs generate              # Scan mods and generate config
/mvs biome                 # Show current biome info
/mvs structure biomes <id> # Show biome rules for a structure
/mvs structure list        # list structures in structure pool
/mvs structure nearby      # list structures next to player   
/mvs help                  # Show all commands
```

See the **[Commands Reference](https://github.com/RhettL/multi-village-selector/blob/master/docs/Commands.md)** for full documentation.

## Supported Mods

MVS works with mods that **add new village structures** to structure sets:

- **Vanilla Minecraft** - All 5 village types
- **Cobblemon Additions (BCA)** - Cobblemon-themed villages
- **ChoiceTheorem's Overhauled Village (CTOV)** 
- **Towns & Towers**
- **Terralith**
- And many more...

Some mods require disabling their own village spawning. See **[Mod Compatibility](https://github.com/RhettL/multi-village-selector/blob/master/docs/ModCompatibility.md)** for setup instructions.

### What MVS Cannot Control

MVS intercepts **structure selection**, not **jigsaw piece assembly**. Mods that replace individual village pieces (buildings, paths) rather than adding whole village structures work differently:

- **Better Villages** - Replaces vanilla village jigsaw pieces
- **Luki's Grand Capitals** - Replaces/extends vanilla village pieces

These mods will still apply their changes to whatever village MVS selects, usually the vanilla villages. This is usually fine if you only use one - MVS picks which village type spawns, then the jigsaw replacer modifies its buildings. But MVS doesn't help you with fighting between those type of mods.

## Documentation

| Guide | Description |
|-------|-------------|
| **[Getting Started](https://github.com/RhettL/multi-village-selector/blob/master/docs/GettingStarted.md)** | Installation and first-time setup |
| **[Configuration](https://github.com/RhettL/multi-village-selector/blob/master/docs/Configuration.md)** | Complete config reference |
| **[Mod Compatibility](https://github.com/RhettL/multi-village-selector/blob/master/docs/ModCompatibility.md)** | Per-mod setup instructions |
| **[Commands](https://github.com/RhettL/multi-village-selector/blob/master/docs/Commands.md)** | In-game command reference |
| **[Spacing Guide](https://github.com/RhettL/multi-village-selector/blob/master/docs/SpacingGuide.md)** | Controlling village density |
| **[Troubleshooting](https://github.com/RhettL/multi-village-selector/blob/master/docs/Troubleshooting.md)** | Common issues and solutions |
| **[Project Scope](https://github.com/RhettL/multi-village-selector/blob/master/docs/Scope.md)** | Design philosophy and limitations |

## Known Bugs

- **No villages near world spawn** - Villages won't spawn within ~8-16 chunks of (0,0). Explore further out.
- **Better Village spacing override** - Better Village mod overrides spacing settings at runtime. See [Troubleshooting](https://github.com/RhettL/multi-village-selector/blob/master/docs/Troubleshooting.md#known-bugs).

See **[Troubleshooting](https://github.com/RhettL/multi-village-selector/blob/master/docs/Troubleshooting.md#known-bugs)** for details and workarounds.

## FAQ

#### Do I need village mods installed?
MVS works with vanilla, but you'll only see vanilla villages. Install village mods like [Cobblemon Additions](https://modrinth.com/mod/cobblemon-additions) or [Towns & Towers](https://modrinth.com/mod/towns-and-towers) for variety.

#### Why aren't my villages spawning?
Check the [Troubleshooting Guide](https://github.com/RhettL/multi-village-selector/blob/master/docs/Troubleshooting.md). Common causes: mod conflicts, biome mismatches, or spacing settings.

#### Can MVS control other structures (temples, mansions)?
Currently MVS focuses on villages. See [Scope](https://github.com/RhettL/multi-village-selector/blob/master/docs/Scope.md) for design rationale. You can add any structure to MVS's pool, but it's designed for only one structure pool.

#### Fabric or NeoForge?
Both! MVS v0.3.0+ supports both platforms via Architectury.

#### Can I add this to my modpack?
Yes! Please file an [issue](https://github.com/RhettL/multi-village-selector/issues) if you have feature requests or find bugs. If it's outside the [project scope](https://github.com/RhettL/multi-village-selector/blob/master/docs/Scope.md), I'll likely say no.

#### Can you do this for all structures?
That's harder. Managing ALL structure pools this way is much more complex. I'm considering it, but honestly comprehensive datapacks are probably easier for that use case.

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Submit a Pull Request

For bug reports and feature requests, use [GitHub Issues](https://github.com/RhettL/multi-village-selector/issues).

## Authors

**Project Design:** [RhettL](https://github.com/RhettL)

**Implementation:** This mod was developed entirely with [Claude Code](https://claude.ai/code) (Claude Opus 4.5), an AI coding assistant by Anthropic. RhettL provided design direction and testing.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- Village mod creators for their amazing structures
- [Architectury](https://architectury.dev/) for multi-loader support
- [json5-java](https://github.com/marhali/json5-java) for config parsing
- [NeoForge](https://neoforged.net/) and [Fabric](https://fabricmc.net/) teams
- Claude Code (Sonnet 4.5) for doing most of the work coding this at RhettL's Direction.

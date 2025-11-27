# Multi Village Selector (MVS)

**Minecraft 1.21.1 | NeoForge & Fabric | MIT License**

Multi Village Selector gives you control over village spawning in Minecraft. Instead of each village mod fighting for spawn slots, MVS intercepts village generation and selects from **all your installed village mods** using configurable weights.

## The Problem MVS Solves

When you install multiple village mods (CTOV, Towns & Towers, BCA, etc.), they compete for the same spawn locations. Some mods override others, some never spawn, and you have no control over the mix. MVS fixes this by:

- **Intercepting** vanilla village spawns before any mod processes them
- **Selecting** from all configured village structures using weighted random selection
- **Respecting** biome rules so desert villages spawn in deserts, snowy villages in snow, etc.

## Download

<!-- TODO: Add Modrinth link after v0.3.0 release -->
- **Modrinth**: Coming soon with v0.3.0 release
- **GitHub Releases**: [Latest Release](https://github.com/RhettL/multi-village-selector/releases)

**Requirements:**
- Minecraft 1.21.1
- NeoForge 21.1.80+ **or** Fabric Loader 0.16.0+ with Fabric API
- [Architectury API](https://modrinth.com/mod/architectury-api) (required for both platforms)

## Quick Start

1. **Install** MVS and Architectury API in your `mods/` folder
2. **Launch** Minecraft once to generate the default config
3. **Run** `/mvs generate` in-game to scan your installed mods
4. **Review** the generated config at `config/multivillageselector.json5`
5. **Restart** Minecraft to apply changes

That's it! Villages from all your mods will now spawn with equal representation.

For detailed setup instructions, see the **[Getting Started Guide](docs/GettingStarted.md)**.

## Configuration

MVS uses a JSON5 config file with three main sections:

```json5
{
  // Which structure sets MVS controls (usually just villages)
  intercept_structure_sets: ["minecraft:villages"],

  // Your village structures with per-biome weights
  structure_pool: [
    { structure: "minecraft:village_plains", biomes: {"#minecraft:is_plains": 10} },
    { structure: "ctov:village_plains", biomes: {"#minecraft:is_plains": 10} },
    // ... more structures
  ],

  // Optional: Control spawn frequency per biome
  biome_frequency: {
    "#minecraft:is_ocean": 0.3,  // 30% spawn rate in oceans
  }
}
```

See the **[Configuration Guide](docs/Configuration.md)** for complete documentation.

## Commands

```bash
/mvs generate              # Scan mods and generate config
/mvs biome                 # Show current biome info
/mvs structure biomes <id> # Show biome rules for a structure
/mvs help                  # Show all commands
```

See the **[Commands Reference](docs/Commands.md)** for full documentation.

## Supported Mods

MVS works with any mod that adds village structures through Minecraft's jigsaw system:

- **Vanilla Minecraft** - All 5 village types
- **CTOV** (ChoiceTheorem's Overhauled Village)
- **Towns & Towers**
- **Cobblemon Additions (BCA)**
- **Terralith**
- **Better Villages**
- And many more...

Some mods require disabling their own village spawning. See **[Mod Compatibility](docs/ModCompatibility.md)** for setup instructions.

## Documentation

| Guide | Description |
|-------|-------------|
| **[Getting Started](docs/GettingStarted.md)** | Installation and first-time setup |
| **[Configuration](docs/Configuration.md)** | Complete config reference |
| **[Mod Compatibility](docs/ModCompatibility.md)** | Per-mod setup instructions |
| **[Commands](docs/Commands.md)** | In-game command reference |
| **[Spacing Guide](docs/SpacingGuide.md)** | Controlling village density |
| **[Troubleshooting](docs/Troubleshooting.md)** | Common issues and solutions |
| **[Project Scope](docs/Scope.md)** | Design philosophy and limitations |

## FAQ

**Q: Do I need village mods installed?**
A: MVS works with vanilla, but you'll only see vanilla villages. Install village mods like CTOV or Towns & Towers for variety.

**Q: Why aren't my villages spawning?**
A: Check the [Troubleshooting Guide](docs/Troubleshooting.md). Common causes: mod conflicts, biome mismatches, or spacing settings.

**Q: Can MVS control other structures (temples, mansions)?**
A: Currently MVS focuses on villages. See [Scope](docs/Scope.md) for design rationale.

**Q: Fabric or NeoForge?**
A: Both! MVS v0.3.0+ supports both platforms via Architectury.

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

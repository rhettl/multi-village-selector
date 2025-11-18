# Multi Village Selector (MVS)

A Minecraft mod that brings **village variety** to your world by intelligently replacing vanilla village spawns with villages from multiple mods, creating a diverse and immersive village experience.

## Features

- 🏘️ **Dynamic Village Variety** - Villages from CTOV, Towns & Towers, BCA, Terralith, and more spawn naturally
- 🎲 **Weighted Selection** - Configure which villages spawn in each biome with custom weights
- 🌍 **Biome-Aware** - Villages automatically match their biome (snowy villages in snow, desert villages in desert, etc.)
- ⚙️ **Highly Configurable** - JSON5 config with full documentation and wildcard pattern support
- 🎯 **Intelligent Spawning** - Includes weighted "empty" spawns for controlling village density in sparse biomes
- ✅ **Compatible** - Works with vanilla jigsaw structures, no special dependencies required

## Quick Start

### Installation

1. Download the latest release from [Releases](https://github.com/RhettL/multi-village-selector/releases)
2. Place `multivillageselector-1.0.0.jar` in your `mods/` folder
3. Launch Minecraft with NeoForge and your favorite village mods installed

### Basic Configuration

The mod creates `config/multivillageselector.json5` on first launch with sensible defaults.

**For best results with CTOV:**
Edit `config/ctov-common.toml`:
```toml
[structures]
    generatesmallVillage = false
    generatemediumVillage = false
    generatelargeVillage = false
```

**For BCA users:**
No configuration needed! MVS automatically detects and replaces BCA villages.

## How It Works

Multi Village Selector intercepts village spawn attempts and replaces them with villages selected from configured pools:

```json5
plains: [
  { structure: "minecraft:village_plains", weight: 10 },
  { pattern: "towns_and_towers:village_*plains", weight: 30 },
  { pattern: "ctov:*/village_plains", weight: 30 },
  { pattern: "bca:village/default_*", weight: 25 },
]
```

Villages are selected randomly based on weights, ensuring variety while respecting biome appropriateness.

## Documentation

- 📖 **[Configuration Guide](docs/Configuration.md)** - Detailed config options and examples
- 🔧 **[Mod Compatibility](docs/ModCompatibility.md)** - Which mods work how with MVS
- ❓ **[Troubleshooting](docs/Troubleshooting.md)** - Common issues and solutions

## Supported Village Mods

- ✅ **Vanilla Minecraft** - All 5 vanilla village types
- ✅ **CTOV** (ChoiceTheorem's Overhauled Village) - 100+ village variants
- ✅ **BCA** (Cobblemon Additions) - Special handling for structure set override
- ✅ **Towns & Towers** - Themed and exclusive villages
- ✅ **Terralith** - Fortified villages
- ✅ **Villages & Pillages** - Additional village types
- ✅ Any mod using vanilla jigsaw structure system

See [Mod Compatibility](docs/ModCompatibility.md) for detailed configuration per mod.

## Requirements

- **Minecraft**: 1.21.1
- **NeoForge**: 21.1.80+
- **Java**: 21
- **Dependencies**: None (village mods are optional but recommended)

## Configuration Example

Control village density per biome with weighted empty spawns:

```json5
ocean: [
  { pattern: "joshie:village_ocean", weight: 20 },
  { pattern: "towns_and_towers:village_ocean", weight: 20 },
  { empty: true, weight: 60 }, // 60% chance no village spawns
]
```

This makes ocean villages rare and special, while keeping land biomes fully populated.

## Performance

MVS operates during world generation and has minimal performance impact:

- ✅ No runtime overhead (only runs during chunk generation)
- ✅ No custom structure placement (uses vanilla systems)
- ✅ Compatible with all structure mods

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/multivillageselector-1.0.0.jar`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Authors

**Project Concept:** [RhettL](https://github.com/RhettL)

**Implementation:** This mod and all code were produced entirely by **Claude Code** (Claude Sonnet 4.5), an AI coding assistant by Anthropic. RhettL provided guidance and design direction but takes no credit for the code implementation.

**Credit Statement:** RhettL accepts no credit for the code, implementation, or technical aspects of this mod. All programming work was performed by Claude Code under RhettL's direction.

## Support & Community

- 🐛 **Issues**: [GitHub Issues](https://github.com/RhettL/multi-village-selector/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/RhettL/multi-village-selector/discussions)

## Acknowledgments

- Thanks to all village mod creators for their amazing structures
- Built with [NeoForge](https://neoforged.net/)
- Config parsing powered by [json5-java](https://github.com/marhali/json5-java)

---

**Note:** This mod requires village mods to provide variety. With only vanilla villages, it will select from vanilla villages. Install CTOV, Towns & Towers, BCA, or other village mods for full effect!

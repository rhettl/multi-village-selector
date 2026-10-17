# Multi Village Selector (MVS) - Development Notes

## Current Status

**v0.4.2 - RELEASED** (MC 1.21.1, NeoForge 21.1.x + Fabric, Architectury)
- GitHub: https://github.com/rhettl/multi-village-selector/releases/latest
- Modrinth: auto-publishing via workflow (stable tags only)

**Released Versions:**
- v0.4.2: Fix `LocateCommandMixin` method-name collision with vanilla `LocateCommand.getHolders`, `@Unique` on mixin helpers, NeoForge update checker via Modrinth
- v0.4.1: Hotfix - restore `StructureMixin` to NeoForge mixin config
- v0.4.0: Placement control, `/mvs locate`, spawn chunk fix, documentation overhaul
- v0.3.2: `/mvs structure list [filter]`, `/mvs structure pool`
- v0.3.1: GitHub + Modrinth release, NeoForge + Fabric, config API stable

**Known Issues:**
- Large structures (terralith:fortified_village) may fail terrain validation on hilly biomes - use `relaxed_biome_validation: true`

**Dev-only YACL config UI** was built and reverted (PR #3). Plan: a new in-game config UI built on owo-lib (see `dev-docs/roadmap.md`). Old code is in git history (`939918c`..`e784bbd`) for reference only.

See `dev-docs/session-log.md` for historical session notes.
See `dev-docs/roadmap.md` for planned work and ideas.

---

## Coding Guidelines

### Test-Driven Development (TDD)
Use TDD where possible. Write tests first, then implement. All new code must be testable within reasonable norms.

### Test Discipline
- **When writing/fixing implementation code**: Do not modify existing tests. If tests fail, fix the code to match the test specification. If you believe a test is wrong, stop and explain why before touching it.
- **When explicitly asked to write tests**: You may create and modify tests freely.

### Testability & Anti-Corruption Strategy
Testability is paramount. Use anti-corruption layers to isolate external dependencies:

- **Models**: Internal data types that shield business logic from external types (Minecraft, Cobblemon APIs)
- **Services/Adapters**: Handle registry access, world queries, external APIs
- **Business Logic**: Pure functions operating on models
- **Commands/Controllers**: Thin orchestration layer only

Isolate external access (registries, world state, APIs, file I/O) into dedicated service/adapter files. Business logic should receive pure data or internal models, not registry handles or Minecraft types directly.

### File Organization
- **Files**: Keep files <800 lines
- **Functions**: Keep functions <200 lines

Distribute code into relevant directories and classes. When uncertain about where code belongs, ask.

### No Side-Effect Mutations
Functions should return results, not mutate their arguments. Avoid pass-by-reference side effects. If a function needs to "change" something, return the new value. Methods on objects may mutate `this`, but standalone functions should be pure.

### Use Existing Libraries
Before writing new code to implement common functionality, check the codebase for existing helpers before creating new ones.

---

## Development Workflow

### IDE (IntelliJ + Architectury Plugin)

Run configurations for both loaders are provided via the Architectury plugin:

- **Minecraft Client (NeoForge)** - launches NeoForge client with mod loaded
- **Minecraft Server (NeoForge)** - launches NeoForge server
- **Minecraft Client (Fabric)** - launches Fabric client with mod loaded
- **Minecraft Server (Fabric)** - launches Fabric server

Plus `Minecraft Client/Server (NeoForge, Integration)` run configs (`run-integration/`, gitignored).

Use these run configs directly from IntelliJ for development and testing. No external Minecraft install needed.

### Build

Kotlin DSL (`*.gradle.kts`), Gradle 8.12, Java 21, Architectury Loom 1.11. Mappings are Mojang + Parchment (`parchment_version` in `gradle.properties`). Needs network on first build (NeoForge sources jar; `--offline` fails).

```bash
# Build all JARs (output in */build/libs/)
./gradlew build

# NeoForge dev-only mods (Sinytra Connector + Forgified Fabric API) into neoforge/run/mods
./gradlew :neoforge:syncDevMods

# Run tests
./gradlew test
```

### Releasing

```bash
# ALWAYS use tag-release.sh - ensures gradle version matches git tag
./tag-release.sh 0.5.0-RC1                    # Auto: update gradle → commit → tag → push
./tag-release.sh 0.5.0 -m "Custom message"    # With custom commit message

# DO NOT manually push tags - gradle version will mismatch!
```

- CI (`build.yml`) runs on pushes/PRs to `master` and `rc`
- RC/dev tags (v*.*.*-*) → GitHub release only, Modrinth skipped
- Stable tags (v*.*.*) → GitHub release + auto-publish to Modrinth when published

**Git:** https://github.com/rhettl/multi-village-selector

---

## Architecture

**Four-layer control system:**
1. **Registry Replacement** - Replace StructureSet with MVS placement values
2. **StructureInterceptor** - Chunk gen guarantee using MVS placement
3. **hasStructureChunkInRange() Mixin** - Spawn exclusion zone fix
4. **mvsLocate() + /locate Mixin** - MVS-aware locate commands

**Key Files:**
```
common/src/main/java/com/rhett/multivillageselector/
  MVSCommon.java                           # Shared init (loader entry points call this)
  mixin/ChunkGeneratorMixin.java           # Structure interception
  mixin/LocateCommandMixin.java            # /locate override
  mixin/StructureMixin.java                # Must be listed in BOTH loaders' mixin json (0.4.1 hotfix)
  StructureSetInjector.java                # Registry replacement
  strategy/MVSStrategyHandler.java         # Strategy dispatch
  commands/<area>/                         # Command groups (structure, biome, locate, debug, ...)
  util/StructureScanner.java               # Registry/JAR scanning (large - see debt)
  config/ConfigLoader.java                 # Config loading + migration
  util/PatternMatcher.java                 # Pattern matching + specificity
  util/LocateHelper.java                   # MVS-aware locate logic
  strategy/StructureInterceptor.java       # Structure-set interception
  strategy/StructurePicker.java            # Weighted selection
  commands/MVSCommands.java                # Command registration
```

Loader entry points: `neoforge/.../neoforge/MultiVillageSelector.java`, `fabric/.../MultiVillageSelectorFabric.java`.

**Components:**
- `ChunkGeneratorMixin` - Intercepts `structure.biomes()`, returns dynamic proxy HolderSet
- `PatternMatcher` - Unified biome/structure matching with specificity scoring
- `StructureInterceptor` - Structure-set interception + biome frequency roll
- `StructurePicker` - Weighted structure selection
- `BiomeRuleResolver` - MVS config → fallback vanilla resolution

---

## Config Format (v0.3.0+)

```json5
{
  enabled: true,

  block_structure_sets: [],
  structure_pool: [
    { structure: "minecraft:village_plains", biomes: { "#minecraft:has_structure/village_plains": 10 } },
    { structure: "ctov:small/village_*", biomes: { "#minecraft:is_beach": 10 } },
    {
      structure: "minecraft:village_plains",
      biomes: {
        "#minecraft:has_structure/village_plains": 10,
        "#minecraft:is_beach": 5,
        "minecraft:desert": 2
      }
    },
  ],
  blacklisted_structures: [],
  biome_frequency: {
    "#*:*": 0.5,                       // 50% default
    "#minecraft:is_plains": 1.0,       // 100% plains
  },
  placement: {
    spacing: 34,
    separation: 8,
    spreadType: "triangular",
    salt: 10387312
  },
  relaxed_biome_validation: false,

  intercept_structure_sets: ["minecraft:villages"],

  debug_logging: false,
  debug_cmd: false,
}
```

**Pattern Matching:** Supports `*:*`, `minecraft:*`, `*village*`, biome tags (`#`), specificity scoring.

---

## Commands

```bash
# Basic
/mvs help                                  # Show all commands
/mvs generate                              # Generate config from mods -> chat button
/mvs generate do-confirm                   # Warning + "Confirm, move now" button (moves nothing)
/mvs generate apply                        # Move local/mvs/ file to config/ (backs up existing)
/mvs biome                                 # Current biome info

# Structure inspection
/mvs structure list [filter]               # Dump ALL structures to file
/mvs structure pool                        # Show MVS config structures
/mvs structure biomes <structure>          # Show biome rules
/mvs structure test <structure> <biome>    # Test spawn validity

# Locate (v0.4.0)
/mvs locate <structure> [more]             # Find nearest structure

# Debug (requires debug_cmd: true)
/mvs debug mod-scan [all]                  # JAR + Registry scan
/mvs debug mod-weights [filter]            # Registry weight analysis
```

**Output:** `local/mvs/` directory

---

## Mod Compatibility

**Village Mod Patterns:**
1. **Datapack Override** - BCA, Terralith (replaces minecraft:villages)
2. **Lithostitched Extension** - CTOV (runtime injection)
3. **Custom Structure Sets** - Towns & Towers (own structure_sets)
4. **Jigsaw Replacers** - Better Villages, Luki's Grand Capitals (modify village pieces, not selection)
5. **Custom Generation** - MineColonies (cannot intercept)

---

## Test Coverage

- **223 tests, 100% passing** (`./gradlew test`)
- **Coverage:** JaCoCo on `common` (mixins excluded), ~15% lines. `./gradlew :common:jacocoTestReport` → `common/build/reports/jacoco/test/html`. `check` enforces a 12% regression floor; raise it as logic is extracted from commands/scanner.
- **Core logic tested:** ConfigParser (41), LocateHelper (32), BiomeRules (24), PredictionHelper (23), PatternMatcher (31), BiomePoolExpander (22), StructureScanner (17), StructurePicker (14)

**Known debt (over size guidelines):** `StructureScanner` (~1650 lines), `MVSCommands` (~1420), `LocateHelper` (~800). `StructurePlacementAccessor` mixin is imported directly from several files.

---

## Design Decisions

1. **Biome Frequency over Empty Weights** - Applied BEFORE structure selection with pattern matching
2. **Unified Pattern Matching** - Specificity scoring: literal > tag > wildcard
3. **Filter-first Selection** - Vanilla retry mode removed (same distribution, 500+ lines less)
4. **Per-mod Normalization** - Each mod gets avg weight 25, preserves internal ratios

---

**License:** MIT with Claude Code attribution
**Repo:** https://github.com/rhettl/multi-village-selector
**Modrinth:** https://modrinth.com/mod/multi-village-selector
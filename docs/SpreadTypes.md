# Spread Types Guide

Spread type controls where within each placement cell a structure can spawn. This affects how clustered or spread out your villages appear.

## Overview

| Type | Distribution | Use Case |
|------|--------------|----------|
| `linear` | Uniform random | Vanilla villages (default) |
| `triangular` | Center-biased bell curve | More consistent spacing |
| `gaussian` | Strong center bias | Tight clustering |
| `edge_biased` | Avoids center | Variable spacing |
| `corner_biased` | Pushed to corners | Extreme variance |
| `fixed_center` | Always exact center | Deterministic grid |

## Diagrams

Each diagram shows probability distribution within a single placement cell. More dots = higher spawn probability.

### linear

Uniform distribution - every position has equal probability.

**When to Choose:** Most of the time. When you want a more random "random".

```
+------------------------+
|........................|
|........................|
|........................|
|........................|
|........................|
|........................|
|........................|
|........................|
+------------------------+
  Equal chance everywhere
```

**Vanilla villages use this.** Structures can spawn anywhere in the valid area with equal likelihood.

### triangular

Bell curve toward center - sum of two random values creates triangular distribution.

**When to Choose:** When you want a greater distance between villages, without strictly limiting.

```
+------------------------+
|                        |
|        ........        |
|      ............      |
|    ......++++......    |
|  .......++++++.......  |
|    ......++++......    |
|      ............      |
|        ........        |
+------------------------+
     Peaks in center
```

**Best for consistent spacing.** Reduces chance of two structures spawning near shared cell boundaries. Some mods like BCA use this.

### gaussian

Strong center bias with rare edge spawns - normal distribution.

**When to Choose:** When you want an **even** greater distance between villages.

```
+------------------------+
|                        |
|                        |
|         ......         |
|       ....++....       |
|      ....++++....      |
|       ....++....       |
|         ......         |
|                        |
+------------------------+
   Tight center cluster
```

**Experimental.** Creates very predictable, clustered spawns. Edge spawns are rare but possible.

### edge_biased

Inverse of triangular - biased toward edges, avoids center.

**When to Choose:** When you want unpredictable spacing - sometimes clustered at shared edges, sometimes far apart.

```
+------------------------+
|++++++++++++++++++++++++|
|++                    ++|
|..                    ..|
|..                    ..|
|..                    ..|
|..                    ..|
|++                    ++|
|++++++++++++++++++++++++|
+------------------------+
   Edges more likely
```

**Experimental.** Structures spawn near cell edges. Adjacent cells may cluster at shared boundaries or end up far apart.

### corner_biased

Pushed toward cell corners - maximum separation.

**When to Choose:** When you want extreme variance - tight clusters at shared corners or maximum separation.

```
+------------------------+
|++++..          ..++++  |
|++++..          ..++++  |
|....              ....  |
|                        |
|                        |
|....              ....  |
|++++..          ..++++  |
|++++..          ..++++  |
+------------------------+
  Corners most likely
```

**Experimental.** Creates extreme variance. Adjacent cells may cluster tightly at shared corners or be maximally separated.

### fixed_center

Always spawns at exact cell center - fully deterministic.

**When to Choose:** When you want zero randomness - a perfect grid.

```
+------------------------+
|                        |
|                        |
|                        |
|           X            |
|                        |
|                        |
|                        |
|                        |
+------------------------+
    Always here (X)
```

**Experimental.** No randomness - every structure spawns at the exact center of its cell. Creates a perfect grid. Useful for testing or specific aesthetic goals.

## Comparison

Side-by-side probability comparison:

```
linear:        triangular:    gaussian:      edge_biased:   fixed_center:
+--------+     +--------+     +--------+     +--------+     +--------+
|........|     |  ....  |     |        |     |++++++++|     |        |
|........|     | ...... |     |  ....  |     |++    ++|     |        |
|........|     |...++...|     | ..++.. |     |..    ..|     |        |
|........|     |...++...|     | ..++.. |     |..    ..|     |   X    |
|........|     | ...... |     |  ....  |     |++    ++|     |        |
|........|     |  ....  |     |        |     |++++++++|     |        |
+--------+     +--------+     +--------+     +--------+     +--------+
 uniform       center peak    tight center   edges peak     exact point
```

## Configuration

```json5
placement: {
  "minecraft:villages": {
    spacing: 34,
    separation: 8,
    spreadType: "triangular"  // or "linear", "gaussian", etc.
  }
}
```

## Choosing a Spread Type

| Goal | Recommended |
|------|-------------|
| Vanilla-like behavior | `linear` |
| More consistent spacing | `triangular` |
| Even more consistent spacing | `gaussian` |
| Unpredictable/chaotic | `edge_biased` or `corner_biased` |
| Testing/debugging | `fixed_center` |

**Note:** `gaussian`, `edge_biased`, `corner_biased`, and `fixed_center` are experimental. They work but are less tested. Report issues on [GitHub](https://github.com/RhettL/multi-village-selector/issues).

---

**See Also:**
- [Configuration](Configuration.md#spread-types) - Full placement config
- [Spacing Guide](SpacingGuide.md) - Spacing and separation

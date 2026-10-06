# Terrain.7: adaptive coastal terrain

Version 0.6.0-terrain.7, Minecraft 1.20.1 / Forge 47.2.0.
The supported terrain combination remains Tectonic 3.0.17, Terralith 2.5.4,
Lithostitched 1.4.11, standard noise generation, sea level 63, without WWOO.

## Changes

- Neighborhood median height, upper-quartile height, relief, and slope are sampled
  from the original preliminary density. Statistics are cached on a 32-block grid
  and smoothly interpolated. Heights are estimates, not measured blocks.
- Low coasts receive gentle relief and irregular shallow pools. Cliff treatment
  grows continuously with original height; there is no per-chunk profile switch.
  Tall coasts retain most inland elevation and retreat primarily at the coastal
  foot. Beach lowering and sand coverage are gated by the resulting elevation.
- Inland density influence fades over 24–48 blocks depending on original height.
  Ocean boundaries retain their smaller fade. Topsoil and rock decoration fade
  toward neighboring inland biomes, using biome queries rather than mutable chunks.
  Common grassy, sandy, badlands and mountain transitions are supported; custom
  biome surface rules may still need specific compatibility tuning.
- Regional plans try six deterministic sites. Arches require a tall profile and
  sample both piers for biome membership and baseline support, and reject openings
  inside the biome transition band. Their orientation
  follows the ocean-distance gradient when available, and roof heights vary.
  Elevated pools require relatively gentle supporting terrain. Existing arches
  and overhangs remain the large formations; a new standalone stack system is not
  part of this build.
- Density cuts preserve existing cave air deeper than three blocks below the
  original surface. Construction remains possible near/above the old surface,
  with a three-block pool-floor liner exception. This avoids rebuilding the full
  underground cliff body into solid columns. Deep density remains unchanged.
- An optional Quark mixin rejects Jasper, Shale and Limestone cluster blocks at
  each placement destination in stony shore or a shore column at sea level + 2.
  This also catches clusters seeded in neighboring biomes. Other Quark features,
  crafted variants, and generation outside those shore columns remain available.
  Compatibility target: Quark 4.0-462. Existing blocks are not removed.
  The three IDs are also filtered out of the configurable shore rock palette.

## Installation and test

Replace the old Stony Shore Revival JAR with the production `*-srg.jar`; keep
exactly one version installed. Restart Minecraft and use a fresh test world.
Existing terrain is not regenerated, and old/new generation borders are a separate
issue from biome transitions. Distant Horizons may retain old terrain in its cache;
judge the new build using fully loaded nearby chunks first.

Test a low coast, a tall cliff, a biome boundary, an arch and a shallow cave.
Keep the improved sandy waterline in view when comparing terrain. If comparing
the same coordinates, use the same seed and mod configuration in a fresh world.

1. `/stonyshore record start` before exploring new terrain.
2. Move directly above each formation and use a label such as:
   `/stonyshore mark low_coast`, `/stonyshore mark cliff`,
   `/stonyshore mark boundary`, `/stonyshore mark arch`, `/stonyshore mark cave`.
3. Each mark captures the currently loaded area within 32 blocks horizontally.
   Its confirmation reports active shore columns and warns when none were sampled.
   For a cave, stand within the cave as well as marking the surface above it.
4. `/stonyshore record stop`, then `/stonyshore audit` once to export the session
   and all saved sites. Attach that ZIP with screenshots matching the labels.

Marks retain the latest 16 labels per dimension in memory until the server closes.
Repeating a label replaces it; eviction is reported. `/stonyshore marks clear`
removes saved sites. Recording and marks are independent; starting a new recording
does not erase marks. Marking never loads missing chunks or edits the world.

## Audit improvements

Format 7 includes `diagnostics/sites/<label>/terrain.json`, `columns.csv`, and
`maps.svg` for each saved site, plus the existing command-location diagnostics.
Column records include original neighborhood statistics, profile weights, and
inland boundary distance. Profiles are explanatory labels over continuous weights.
Adapter status also reports whether the optional Quark cluster hook has run and
the process-wide count of placements it rejected; these counters confirm activity,
not the number of removed blocks in a particular world.

Noise-settings and density-function exports unwrap runtime holder nodes in an
export copy before encoding. The live terrain graph is not modified. Other
third-party codec or archive-size failures remain separately reported.

Automated geometry checks cannot establish full-pack appearance, biome blending
in every custom biome, feature frequency, or generation throughput. The next
in-game pass should verify these in fresh chunks with named site measurements.

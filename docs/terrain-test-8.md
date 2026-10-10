# Terrain.8: coastal reconstruction and feature recovery

Version 0.6.0-terrain.8, Minecraft 1.20.1 / Forge 47.2.0.
Supported terrain stack: Tectonic 3.0.17, Terralith 2.5.4, Lithostitched 1.4.11,
sea level 63, standard noise generation, without WWOO. Quark compatibility target
is 4.0-462. This remains a development build for fresh-world testing.

## Changes from terrain.7

- Monotone cubic reconstruction replaces interpolation that flattened slopes
  at every 16-block height and 32-block profile grid line. It preserves a linear
  slope and bounds steep transitions between their samples.
- Coastal classification uses surface climate depth, independently of cave
  biomes. This addresses inactive columns beside shores with underground biomes
  at Y65. Actual biome placement and the inland material transition are retained.
- Ocean-distance nodes now interpolate linearly, and density influence extends
  into a bounded ocean apron. This removes the exact ocean-biome cutoff at the
  waterline. Distant ocean and inland density queries bypass terrain planning.
- Regional features try twelve independent sites and alternate arch orientations.
  Smaller arches can fit partial coastal influence; supports are checked against
  original final density, including carved caves. Both approaches are lowered
  with the opening. Overhangs require a descending slope and supporting rock.
- Explicit tide pools have small, medium and large candidates, with small pools
  favored. Gentle shelves up to 76 blocks above sea level can qualify. Their
  floor and rim are checked before construction. Whole-footprint overlap rules
  prevent neighboring features from cutting through a pool's rim. Pool water
  owns its shallow basin even within partial coastal influence.
- Sandy bench selection occurs before the elevation gate, so selected beaches
  can lower the foot of a tall cliff. The bench is strongest within 26 blocks of
  the ocean and fades out by 42; distant cliffs retain elevation. Existing
  beach_frequency values persist; the default remains 0.65.
- Cave air below the shallow surface shell is preserved, except for the existing
  bounded pool-floor liner. Features with missing support are rejected.

Candidate rates describe attempts, not guaranteed frequency. Automated geometry
tests establish behavior in controlled terrain; appearance and frequency in this
customized pack still need the next in-game pass.

## Install and compare

1. Close Minecraft. Replace the terrain.7 JAR with
   `stony-shore-revival-0.6.0-terrain.8-srg.jar`, keeping exactly one version.
2. Restart and create a fresh world using the same seed and pack configuration
   for comparison. Existing generated chunks are not repaired.
3. Judge nearby, fully loaded terrain first. Distant Horizons may display older
   or incomplete terrain; distant boxes are not sufficient evidence of a density
   defect. Include a view without shaders when material boundaries are unclear.
4. Start `/stonyshore record start` before exploring fresh chunks. Check low
   shores, tall cliffs, ocean waterlines and inland transitions. Look for arches
   open at both ends, overhangs open to the coast, small pools, elevated pools and
   broad sand benches below tall cliffs.
5. Stand directly above each formation and run `/stonyshore mark <label>`.
   Suggested labels: `low_coast`, `cliff`, `boundary`, `arch`, `overhang`,
   `small_pool`, `high_pool`, `beach`, `cave`. Match screenshots to those labels.
   Mark a cave from inside and from above when possible.
6. After exploration, run `/stonyshore record stop` and `/stonyshore audit`.
   Supply the audit ZIP and screenshots for comparison.

Marks capture only currently loaded chunks within 32 blocks horizontally. Repeat
labels receive suffixes (`cliff_2`, `cliff_3`) instead of replacing earlier sites.
Up to 16 sites per dimension are retained in memory; eviction is reported.
Export before exceeding 16, or clear with `/stonyshore marks clear` between
batches. Restarting the server clears the in-memory marks. Recording and marks
are independent; starting a recording does not erase saved sites.

## Diagnostics to check

Audit archive format 8 retains keyed child density references rather than
expanding the entire graph repeatedly. Referenced registry entries are exported
separately. The live graph is not modified. Third-party codec and size failures
remain reported in the archive.

Site terrain format 3 reports surface-climate shore membership independently of
biomes at Y65 and the measured floor, ocean/inland distances, terrain influence
and feature influence. Arch and overhang checks include opening transects plus
roof samples. Pool checks report dimensions, water plane, expected water columns,
measured water columns, unloaded samples and air beneath the expected floor.
These loaded-block measurements can include later mods, structures or player
edits; sampled transects do not prove complete three-dimensional connectivity.

The optional Quark hook now reports constructed generators separately from
placement-hook activity and rejected Jasper/Shale/Limestone placements. A zero
placement count alone does not prove the mixin failed or that a cluster was
attempted. Existing stone blocks are not removed. Keep these process-wide
counters distinct from actual feature counts at saved sites. Planning counters
also include repeat evaluations after cache eviction.

For the next audit, record whether each named feature is present in loaded
blocks, whether pools retain water after revisiting, whether any waterline has an
abrupt straight edge, and whether generation remains responsive. Check inland
blending across grass, badlands, cold biomes and the supplied cave-biome sites.

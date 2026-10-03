# 0.5.0-terrain.1 — first live noise-stage test

This is an experimental geometry test based on the **post-WWOO** audit. It is not a
finished V5 release. Use a fresh disposable test world with your current pack.
Replace audit.2 with this JAR; keep only one Stony Shore Revival JAR installed.

`generation.coastal_terrain` defaults to true and requires a restart when changed.
It is guarded to the audited Tectonic/Terralith/Lithostitched versions, the standard
noise generator and multi-noise biome source, Overworld, sea level 63, and no WWOO.
Unsupported setups keep V4 behavior and record why the adapter was skipped.

## Expected changes

- Noise-stage shaping across chunk boundaries, using world-seeded fields rather
  than a per-chunk ellipse placement loop.
- Lower stony-shore shelves acquire broad depressions and connected irregular
  basin outlines. Water comes from the existing generator/aquifers at sea level.
- Only carving: no filled caves or added terrain. The maximum intended lowering
  is eight blocks; the new floor cannot be below sea level minus three.
- Shores solid at sea+14, submerged columns, and columns with sampled cavities
  at sea-4 through sea-6 are excluded. Tall continental coasts are deliberately
  outside this first test; wider coastal lowering is a subsequent milestone.
- Existing terrain blending regions are left to Minecraft. Existing chunks are
  not rewritten, so a fresh test world gives the clearest comparison.
- Surface texture remains active. Legacy tiny pools and spires are suspended when
  the adapter installs, to make the geometry test readable. New spire and geology
  systems are still pending; their old config values are preserved.

## What to return

Explore both low island shores and low continental stony shores in new chunks.
Run `/stonyshore audit` after exploring and attach the ZIP with screenshots and
coordinates of representative pools and any broken boundaries, dry depressions,
water leaks, unwanted cave openings, structure damage or slow generation.

`resolved/dimensions/minecraft/overworld.json` includes `coastalTerrain` with the
installation status, whether the router is still wrapped, any sampling failure,
and planned/eligible column cache-miss counters. Counters are diagnostic activity,
not unique locations or placed pool counts. The exported generator/registry JSON
continues to describe the original persistent graph; the adapter is runtime-only.
If status says skipped/inactive, report it rather than searching for new geometry.

## Validation limits

Automated tests verify planner determinism and bounds, biome/altitude exclusions,
carving-only behavior, cavity rejection, cache eviction consistency and error handling.
Compilation verifies the Forge/Minecraft method signatures. It does not verify the
private-field adapter, full-pack generation performance, actual pool water filling,
biome-edge appearance or the interactions above. The next in-game test is required.

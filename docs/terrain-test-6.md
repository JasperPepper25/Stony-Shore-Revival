# Terrain.6: native coastal composition and expanded diagnostics

Version: 0.6.0-terrain.6, Minecraft 1.20.1 / Forge 47.

## Terrain integration

Two `lithostitched:wrap_noise_router` modifiers, priority 1100, install registered
`stonyshorerevival:coastal_density` nodes in the preliminary and final density
fields before `RandomState` seeding. The loaded Tectonic/Terralith graph is the
wrapped input. There is no reflective replacement of the live seeded router.
The level-load adapter binds the seed, biome source and bounded regional model
to the seeded nodes. Unbound nodes compute their original input.

Terrain is currently enabled only for the audited Tectonic 3.0.17, Terralith
2.5.4, Lithostitched 1.4.11 combination, sea level 63, without WWOO, and the
standard noise generator/multi-noise biome source. The original biome placement
and climate fields remain intact. This release does not enlarge biomes.

The coastal model constructs solid terrain as well as carving it. Original
density is preserved outside stony shore and smoothly blended at both land and
ocean boundaries. Below sea level minus 24, existing density is retained; the
coastal body blends in over the next 12 blocks. Original old-world `Blender`
contexts also bypass the coastal node. This is not a retrofit of existing chunks.

Shared bounded caches replace per-worker copies. Preliminary-density height
estimates use a 16-block horizontal grid and bounded vertical crossing probes;
they remain estimates, not measurements. Planning never loads chunks. Exact
biome checks at Y65 and the estimated original surface constrain the influence.

Regional shelves have elevations relative to nearby modeled ground, with a
maximum eight-block perimeter deviation. Pool depth is 2–4 blocks below one
water plane. Basins have constructed floors; water handling retains the pack
aquifer and fills qualified basin air. Arch approaches are carved open as part
of their geometry, and overhangs retain a constructed roof. Neighboring region
plans are evaluated across cell edges. Shelf, arch and overhang candidates have
separate selection tests, with overlap rejection where their volumes conflict.

The existing Forge surface and decoration passes remain: sand and underwater
material continuation, supported sand caps, ore cleanup in those caps, optional
Terrain Slabs recoloring, stone palettes, moss and arch vegetation. The old
feature-only pool/spire path remains a fallback when the native model is inactive.

`generation.coastal_beach_frequency` controls the broad beach field (default
0.65); it does not control biome rarity. Restart after generation config changes.

## Test procedure

Install the production `*-srg.jar`, replacing the previous Stony Shore Revival
JAR. Use a fresh world for comparable results. Changing terrain in an existing
world can leave borders between old and new chunks even with smooth biome edges.

1. Run `/stonyshore record start` before exploring new chunks.
2. Explore shore areas containing low pools, upper ledges, beaches and arches.
3. Run `/stonyshore record stop` to retain the session without further samples.
4. Run `/stonyshore audit` near each formation or problem area; attach the ZIPs.
5. `/stonyshore record status` reports whether recording is active and its size.

Starting recording replaces the previous session. Sessions are in memory and
do not survive restarting the server. At most 256 chunk samples are retained;
evictions are reported. An audit inspects only currently loaded chunks. Recording
adds selected history; it is not a scan of the entire world or all visited blocks.

Format-6 audit additions:

- `diagnostics/terrain.json`: named column samples, climate values, source and
  surface biome IDs, original-height estimates, proposed heights, influence,
  water expectations and observations, excavation depth, and sampled arch/
  overhang opening/roof checks.
- `diagnostics/columns.csv`: the same main measurements in a compact table.
- `diagnostics/maps.svg`: planned height, measured floor height, influence and
  sand coverage maps; hover cells for coordinates.
- Two vertical cross-sections in `terrain.json`: original and modified raw
  density plus actual block states. Raw density excludes later structure beard,
  aquifer, surface and feature changes; a mismatch is not automatically a bug.
- `diagnostics/generation-recording.json`: session start/export counters,
  constructor counts, feature timings and elapsed constructor-to-loaded samples.
- Resource stacks now include Lithostitched worldgen modifiers and biome
  injectors, exposing contributing files and priority declarations.

Timing limits: feature nanoseconds measure our feature work. Constructor-to-load
time includes every mod, chunk dependency waits and scheduling; noise constructors
can also be height queries. Neither is isolated terrain CPU time. Ground planning
nanoseconds and cache counters describe evaluations, not unique landforms/chunks.
Concurrent cache misses can duplicate an evaluation; eviction causes reevaluation.
Read-only audits populate planning caches and counters, so export the recording
snapshot before the detailed samples. Export timestamps and coordinates are
included, but the seed, inventories and existing logs are not.

Codec export failures in another runtime density wrapper remain nonfatal and
are listed in `completion.json`; resource stacks and loaded-block measurements
are exported independently. Verification of sampled openings/roofs does not
prove whole-feature connectivity. Full-pack appearance, throughput and standalone
seed-based LOD generation need in-game comparison; synthetic tests alone cannot
establish those properties.

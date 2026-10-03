# Current pack audit — October 2, 2026, 21:06 UTC

The 21:06 UTC audit supersedes the 19:19 UTC baseline below. The coastal adapter
reports installed, still wrapped, and no sampling failure; 5,784,338 planning cache
misses and 76,316 eligible misses are activity counts, not unique columns or pools.
The export completed without reported read/size errors. WWOO remains absent.

Tectonic config has changed: `erosion_scale` is **0.25** (was 0.3) and
`elevation_boost` is **0.15** (was 0.4). The encoded Overworld generator, Overworld
noise settings, shared density-function entries and shore feature list are unchanged.
Identical serialized graphs do not imply identical terrain when config-backed
densities have changed. Keep the newest config as the testing baseline.

Screenshots show filled pools, dry hollows, and a strong horizontal transition.
The minimap reports camera coordinates, not basin-floor or seam elevations. This
audit has no block sample or world seed, so it cannot prove why each hollow is dry
or attribute the entire cliff line to either mod. Code inspection does establish
that terrain.1 cuts shaping off at Y=77, rounds inferred height to integer blocks,
and steps the boundary stencil every four blocks. Its aquifer is free to choose air
even where the new cap carves below sea level. Terrain.2 addresses those mechanisms
and adds loaded-block observations to the next audit.

## Previous 19:19 UTC baseline


The newer audit supersedes the 15:20 UTC report. William Wythers' Overhauled
Overworld was intentionally removed. Do not design compatibility around its features.
Source ZIP SHA-256: `7b7b89c7d99e50e62edd8c424c02a1bcee3c9f367542764e47de97f105d4f331`.
The export has 5,148 entries and no reported read, size or encoding failures.
Raw user archives and full mod lists are not committed to this repository.

## Confirmed active setup

- Minecraft 1.20.1 / Forge 47.4.16; Tectonic 3.0.17; Terralith 2.5.4 (edited JAR).
- Lithostitched 1.4.11; TerraBlender 3.0.1.10; Biolith 1.0.1-beta.1;
  Biome Replacer 3.1-hippo; Streams Reflowing 2.13.8; Terrain Slabs 4.1.1-beta.
- Overworld generator is `NoiseBasedChunkGenerator`; biome source is `MultiNoiseBiomeSource`.
- Noise settings key is `minecraft:overworld`, supplied by `overlay.terratonic`.
- Stony-shore biome JSON now comes from the edited Terralith JAR.
- The encoded Overworld generator, noise settings, Tectonic config and every shared
  resolved density-function entry are byte-identical to the previous audit.
- WWOO's `beach_cliffs` and `stony_shore_tuff` features are absent. Several crop
  features differ too; removal of WWOO is not assumed to be the cause of every difference.
- Tectonic settings include sea level 63, world range -64..639, vertical scale 1.695,
  elevation boost 0.4, and enabled aquifers. These are observed settings, not new defaults.

## Terrain graph

`tectonic:base_terrain` is the maximum of `tectonic:sloped_cheese` and
`terralith:overworld/extra_terrain_sum`, supplied by the Terratonic overlay.
The final density graph also includes Tectonic caves, noodle caves, underground
rivers, lava tunnels and vertical/world blending. Altering only a raw continental
height spline can miss Terralith's extra terrain contribution.

Island and continental offset splines are distinct. The final offset applies
vertical scaling to nonnegative continental offsets through the continental branch;
the island branch follows its own path. That supports treating the user's island
versus continent observation seriously, but it does not identify the source of the
reference pools or prove a single cause for their appearance.

The audit includes 28 empty `key` values in serialized Tectonic config constants.
The original winning resource JSON retains keys such as `vertical_scale` and
`elevation_boost`; the copied config supplies their values. Therefore the resolved
JSON is diagnostic, not a lossless replacement datapack to copy into the mod.
Lithostitched's runtime wrapper codec can also serialize its original graph while
executing a wrapped graph. Runtime status must be reported separately.

## Later passes to retain and test

Streams Reflowing schedules carving at LAKES, water-bank work at LOCAL_MODIFICATIONS,
vegetation at VEGETAL_DECORATION, and flow/snow handling at TOP_LAYER_MODIFICATION.
Terrain Slabs schedules its feature at UNDERGROUND_STRUCTURES. These are registry
observations, not proof that a feature modifies every shore chunk. The first prototype
runs at noise evaluation, before these passes. No blanket removals of their features.

## Adapter decision for terrain.1

Use a per-Overworld runtime density adapter installed during Forge's level-load
lifecycle, before spawn preparation. It retains the seeded loaded router and biome
sampler, caps final density only in eligible low stony-shore columns, and applies the
same cap to preliminary surface estimates. It does not replace the generator,
biome layout, entire noise-settings registry, aquifer channels or ore channels.
The ordinary structure beard is applied by NoiseChunk outside the wrapped final
router density. This reduces interference; it does not guarantee structure safety.

The adapter uses a single isolated, version-specific access to RandomState's router
field (`f_224548_`) through Forge's remapped reflection helper. This is an experimental
compatibility point, not a general-purpose supported API or compatibility guarantee.
Lithostitched 1.4.11 does have density wrapping (verified in its 1.20.1 source at
`bc8029471a7e4b2f00a2091d287d781397408a25`), but registry wrappers alone do not supply
the live biome-source/sampler needed for exact biome restriction in this prototype.
A global climate approximation would risk modifying other biomes in this pack.

Sources inspected: Forge 1.20.1 MinecraftServer level-load event patch and Forge's
ObfuscationReflectionHelper; Lithostitched 1.20.1 WrapDensityFunctionModifier,
DensityFunctionWrapper and MergedDensityFunction. Full-pack runtime validation
remains required, particularly with concurrent generation and Streams Reflowing.


## October 3 terrain.2 feedback and terrain.3 work

The 03:45:59 UTC audit (SHA256 `6ffe0c0beedcfc19bd6783328f570c356d3183b08f506e803adc904f91ad3155`)
is complete and reports the adapter installed, `samplingFailed=false`, and
`routerStillWrapped=true`. WWOO remains absent. Tectonic elevation boost is 0.15
and erosion scale is 0.25. Water attachment/decision counters are evaluations,
not unique features. The local sample at -7315/-1527 contains 576 river, 469 ocean
and 44 temperate-grove columns, zero stony-shore columns, and no unloaded skips.
It therefore cannot establish filling rates at the photographed locations.

User testing reports successful natural pools and smooth transitions. The new
request is elevated terraced pools, cliff-attached arches, wider cliff-foot
beaches, debris/vegetation, and sand extending underwater. See
[terrain.3 design and validation limits](terrain-test-3.md).

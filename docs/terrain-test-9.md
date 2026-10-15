# Terrain.9: seaward coastal formations

Version 0.6.0-terrain.9, Minecraft 1.20.1 / Forge 47.2.0.
Supported terrain stack: Tectonic 3.0.17, Terralith 2.5.4, Lithostitched 1.4.11,
sea level 63, standard noise generation, without WWOO. Quark compatibility target
is 4.0-462. This remains a development build for fresh-world testing.

## Changes from terrain.8

- Coastal terrain can extend into nearby ocean without changing biome placement.
  Ocean shaping has a bounded 56-block footprint and fades back to the original
  terrain. The extent is measured from the sampled biome boundary, so its distance
  from the visible waterline varies with the underlying terrain.
- Beaches preserve more of the cliff behind them. Their contours vary gradually,
  and an underwater shelf transitions from the beach into deeper water offshore.
  Beach surfaces prioritize sand, with occasional coherent stone patches. Grass
  remains appropriate on cliff tops and the inland transition.
- Coastal arches use a rock fin attached to an existing cliff and projecting
  seaward. The passage cuts across the fin, with a substantial roof and outer
  support. Unsuitable sites are rejected rather than excavating a deep approach
  through the cliff. New rock volumes are part of the terrain density shape.
- Overhangs are pronounced cliff shelf projections facing the ocean. A deep cave
  is not required beneath them. Their attachment, thickness and underside vary
  across the feature rather than forming thin uniform horizontal strips.
- Tide pools use multiple placement opportunities and irregular outlines on
  supported shelves at different elevations. Their bowl has a readable wet
  interior, including shallow pools, rather than collapsing to one wet column.
  Rim and floor containment remain necessary; attempt rates are not guaranteed
  placement frequencies.

Automated geometry checks cover controlled terrain and package correctness.
They cannot establish the appearance, feature frequency, water retention or
generation speed of the complete customized pack. The supplied custom builds
are the visual target for this in-game comparison.

## Install and compare

1. Close Minecraft. Replace the terrain.8 JAR with
   `stony-shore-revival-0.6.0-terrain.9-srg.jar`, keeping exactly one version.
2. Restart and create a fresh world using the same seed and pack configuration
   for comparison. Existing generated chunks are not repaired. Biome sizes and
   placement settings do not need to be changed for this build.
3. Judge nearby, fully loaded terrain first. Distant Horizons may display older
   or incomplete terrain. Include a view without shaders when material
   boundaries or pool depth are unclear.
4. Start `/stonyshore record start` before exploring fresh chunks. Inspect both
   narrow and broad shores, low coasts, tall cliffs, offshore shallows and inland
   transitions. Return to pools after leaving and reloading their chunks.
5. Stand directly above each formation and run `/stonyshore mark <label>`.
   Use matching labels for screenshots. For an arch, include both sides and
   its cliff attachment; for an overhang, include the side profile and underside;
   for a pool, include an overhead view showing the actual water area.
6. After exploration, run `/stonyshore record stop` and `/stonyshore audit`.
   Supply the audit ZIP and screenshots for comparison.

Suggested labels are `cliff`, `beach`, `shallows`, `arch`, `overhang`,
`small_pool`, `high_pool`, `boundary` and `unnatural`. Include more than one
example of the feature types where possible. Mark separate pools on a shared
shelf separately when their water levels differ.

Marks capture only currently loaded chunks within 32 blocks horizontally. Repeat
labels receive suffixes instead of replacing earlier sites. Up to 16 sites per
dimension are retained in memory; eviction is reported. Export before exceeding
16, or clear with `/stonyshore marks clear` between batches. Restarting the server
clears the in-memory marks. Recording and marks are independent.

## What to assess in the next audit

- Cliffs retain their height and substantial rock body behind beaches. Look for
  deep shafts, narrow horizontal strips, rectangular terraces and long straight
  shelf edges, especially near feature attachments and biome transitions.
- Beaches are primarily sand with occasional stone patches. Large grassy areas
  should not interrupt the central beach. Check that the waterline and shallow
  seabed descend gradually rather than ending at a steep uniform edge.
- Arches are substantial attached formations projecting toward the ocean. The
  passage should be open across the fin; the cliff connection and outer support
  should look like part of one coherent landform. Check for forced excavations.
- Overhangs project beyond the face below them and retain a thick attachment.
  They should read as cliff shelves from a side view, whether or not a natural
  cavity happens to occur underneath.
- Pools contain visible water over useful areas, have varied outlines and sizes,
  and occur on more than one shelf elevation. Check that water remains contained
  after revisiting and that later decorations do not obscure the entire basin.
- Offshore formations blend into neighboring ocean and leave distant ocean
  unchanged. Check that generation stays responsive while exploring fresh chunks.

## Diagnostic limits

Site terrain format 4 includes attached fin roots and projecting shelf lip
measurements alongside opening and roof transects. Audits retain density
references, profiles, cross-sections, and planned versus measured pool water.
They do not modify
the live world or scan unloaded regions. Loaded blocks can include later mods,
structures and player edits, and sampled transects do not prove full 3D
connectivity. Third-party density codec and size failures remain reported.

Quark generator construction, placement-hook activity and rejected stone
placements are separate counters. Zero placement activity does not by itself
prove a failed mixin or an attempted cluster. Planning counters can include
repeat evaluations after cache eviction and are not unique feature counts.

# 0.5.0-terrain.3 — terraces, arches and cliff-foot beaches

Replace terrain.2 with `stony-shore-revival-0.5.0-terrain.3-srg.jar`, keep only
one Stony Shore Revival JAR, restart, and use a fresh disposable test world.
Existing generated/pregenerated chunks retain their old terrain. Keep terrain.2
for comparison. The audited Tectonic 3.0.17, Terralith 2.5.4, Lithostitched 1.4.11,
Overworld, generator-class, sea-level and no-WWOO guards remain in place.

## Changes

- Retain the low-coast relief from terrain.2. Above it, carve uneven terrace bands
  along stony shores using distance from ocean and broad seeded variation. The
  influence extends inland over roughly 96 blocks, with at most 96 blocks of
  lowering. Fade into adjacent land biomes. Check the biome at both the coastal
  datum and the original surface before reshaping a high column.
- Plan larger elevated pools in 96-block regions, trying varied elliptical,
  irregular footprints. Each accepted pool has one water level and a 2–4 block
  core depth. Check the complete footprint/rim and solid original floor before
  carving; reject cave-punctured or excessively sloping sites as a whole. Plans
  use world coordinates and can span chunk boundaries. Not every terrace qualifies.
- Carve occasional arches through suitable headlands. Require two low open
  portals, substantial original piers and roof support. Uneven, tilted openings
  vary in width/height; surrounding rock is reserved from terrace lowering.
  Add mossy roof/ledge material, occasional hanging glow berries, vines and
  persistent leaf clumps. Arch frequency is deliberately conservative and needs
  in-pack feedback. This does not create freestanding rock rings on every shore.
- Retreat selected cliff feet into broader beach benches, with occasional shallow
  wet pockets. Cover supported ground with two sand layers and sandstone below.
  Sand dithers at margins and fades down to about 11 blocks below sea level.
  A material-only pass extends it up to 12 blocks onto adjacent shallow ocean
  floors, including across chunk boundaries; ocean terrain density is untouched.
- Add sparse Twigs pebbles when available, mossy talus, dead bushes and occasional
  horizontal Quark hollow oak logs (stripped oak fallback). Validate unoccupied,
  supported footprints before writing. Decoration writes stay in the origin chunk.
- Existing fluids and positive final density, including structure influence, keep
  their aquifer decisions. Elevated water is limited to validated new basins;
  the global coastal fluid type must still be water. Legacy V4 geometry stays
  suspended while the terrain adapter is installed.

New restart-required settings under `[generation]`:

```toml
coastal_landforms = true
coastal_arches = true
```

Set `coastal_landforms=false` to return to terrain.2-style shaping in future
chunks (also disables new decoration/aprons). `coastal_arches=false` disables
arch planning independently. `coastal_sandy_shelves=false` disables beach shaping
and sand placement. These settings do not undo generated blocks.

## Test and audit

Compare several low and high stony shores. Check terrace rims and pool drainage,
beach width, submerged sand continuity, arches at headlands, biome borders, and
chunk-generation time. High cliffs are not guaranteed to acquire beaches: the
carving bound, nearby terrain and biome guards can prevent them.

Stand beside the pool/arch and run `/stonyshore audit`. Format 4 includes command
X/Y/Z, planned water levels and arch-candidate flags. The usual loaded-only
65×65-block sample keeps Y=59..87 near the coast; above Y=87 it samples from 32
blocks below to 16 above the command location, bounded by build height. Stand
near the feature rather than far above it. No seed, inventory or player identity
is collected. Counters count evaluations, not unique pools or blocks.

## Evidence and limits

The October 3 03:45 UTC terrain.2 audit reports the adapter installed, no sampling
failure, and attached water handling. Its local sample at X=-7315/Z=-1527 contains
river, ocean and temperate grove, with no stony-shore columns, so it cannot measure
the photographed pools. User testing reports successful filling and smooth
transitions. The sand cutoff in the old pass was directly confirmed in source.

Automated checks cover deterministic planning, biome exclusions, carving bounds,
elevated water ownership, cave rejection, contained regional footprints,
arch acceptance/rejection and submerged apron limits. Synthetic terrain is not
full-pack world generation. Raw density estimates differ from Minecraft's
interpolated blocks, and later features can alter roofs, rims or water. The new
broader planning costs more than terrain.2; caches are bounded per worker. This
is an experimental test build, with visual quality, performance and full-pack
interactions still requiring in-game verification.

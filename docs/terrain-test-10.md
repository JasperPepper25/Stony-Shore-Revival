# Terrain.10: a shared physical coastline

Version **0.7.0-terrain.10**, Minecraft 1.20.1 / Forge 47.2.0 development build.

The terrain adapter targets Tectonic 3.0.17, Terralith 2.5.4 and Lithostitched
1.4.11 together, standard Overworld noise generation, sea level 63, without WWOO.
The biome adapter supports TerraBlender 3.0.1.10 when present. Unsupported
generator layouts or terrain versions leave the native adapter inactive;
unsupported TerraBlender versions leave biome coordination inactive. Audit
status reports these conditions. This is a fresh-world testing build.

## Changes from V9

- **Terrain follows the physical waterline.** A fixed world-coordinate lattice
  reconstructs the unmodified final upstream density field, including paired
  Tectonic/Terralith additions. Connected ocean-facing waterline segments guide
  coastal distance and blended cliff relief. Biome labels select eligible rocky
  coasts, but their boundaries no longer define the beach outline. A shore label
  with no physical ocean waterline cannot create a beach pad.
- **One profile connects dry beaches and offshore shallows.** Beach selection,
  terrain height and sand share a continuous field. The underwater apron deepens
  and fades back to the original floor within 96 blocks of the original coast.
  A narrow beach toe leaves the main cliff behind it; accepted arch and overhang
  attachments reserve their original supporting rock. Broad sand areas have
  occasional coherent stone patches. Sand finishing preserves ores.
- **Local surface biomes follow the same plan.** Eligible rocky coasts can extend
  into neighboring inland biomes through a configurable band, default 96 blocks.
  Dry sand favors beach or snowy beach; rocky surfaces favor stony shore or an
  explicitly supported rocky coast. Submerged ocean variants, rivers and known
  or tagged caves retain their upstream selection. Surface changes can also
  affect vegetation and other biome-dependent features; inspect these alongside
  the terrain rather than judging the biome name alone.
- **Arches have complete seaward bodies.** The projecting fin has a rounded outer
  cap, a bounded basal floor and a thick outer pier. Its opening sits inside the
  body. The complete accepted formation can cross a local terrain-mask edge;
  it is not sliced off at a biome boundary. Passage carving stays in the added
  formation above underlying terrain, avoiding forced inland access shafts.
- **Overhangs find actual cliff edges.** Bounded seaward searches move candidate
  roots to a real descending face. A thick projecting shelf needs clearance
  underneath, but does not require a cave. Its collision bounds include the lip.
- **V9 pool tuning is preserved.** Pool construction, sizes and candidate tuning
  are unchanged. Terrain and biome changes can still alter which shelves qualify,
  so compare observed frequency and containment on the new coast.

## Compatibility approach and current boundary

Tectonic and Terralith continue to supply the base terrain and climate. The
existing Lithostitched pre-seeding density composition adds our bounded coastal
shapes. Classification always queries the immutable upstream biome selection;
it cannot feed our adjusted selection back into coastal planning.

The biome adapter leaves the existing biome-source codec, TerraBlender region
selection, climate noise and possible-biome set intact. It adjusts the final
surface selection only inside an eligible physical band. Targets must already
be in the original possible-biome set so feature ordering stays valid. The
runtime binding is rebuilt on load and cleared on unload.

This is the first coordinated coastal redesign. It controls local terrain and
coastal biome extent; it does not globally enlarge every stony-shore climate
interval, rescale other mods' biomes or replace continent generation. The
[research notebook](research/worldgen/README.md) describes wider experiments.
Untagged third-party cave biomes need additional support. Listing a rocky biome
as compatible does not establish compatibility with every feature of its mod.

## Settings

New settings in `config/stonyshorerevival-common.toml`, under `[generation]`:

```toml
coastal_terrain = true
coastal_biome_coordination = true
coastal_inland_width = 96
compatible_rocky_coast_biomes = ["regions_unexplored:chalk_cliffs"]
```

The inland width accepts 48 through 160 blocks, measured from the reconstructed
original waterline. The outer 32 blocks fade back into neighboring terrain.
Biome replacement uses a stronger interior threshold and therefore occupies
less than the full terrain band. Ocean terrain has its own 96-block envelope.
Existing beach, landform, arch and tide-pool toggles remain available. Settings
require restarting; changes affect future generation. Keep defaults for the
first comparison so the audit describes this build's intended baseline.

## Install and compare

1. Close Minecraft and replace V9 with
   `stony-shore-revival-0.7.0-terrain.10-srg.jar`. Keep exactly one Stony Shore
   Revival JAR. The plain development JAR and source ZIP are not installable.
2. Create a **fresh test world** with the same seed and pack configuration used
   for V9. Existing chunks are not repaired. A new world avoids comparing old
   biome data and terrain against the new generation rules.
3. Inspect fully loaded nearby terrain. Include one view without shaders;
   Distant Horizons may show older or incomplete distant terrain.
4. Start `/stonyshore record start` before exploring. Check low shores, tall
   cliffs, warm/cold beaches, underwater transects and inland transitions.
5. Stand directly above each formation and use `/stonyshore mark <label>`.
   Suggested labels: `beach`, `shallows`, `cliff`, `arch`, `overhang`, `high_pool`,
   `boundary`, `unnatural`. Photograph both arch sides and its outer pier, and
   an overhang's side profile and underside. Capture vegetation boundaries too.
6. Revisit pools after leaving and reloading their chunks. Run
   `/stonyshore record stop` and `/stonyshore audit`, then supply the ZIP and
   matching screenshots.

Up to 16 marks per dimension are kept in memory. Export before exceeding that
limit; repeated labels receive suffixes. Restarting clears marks. Recording
and marks are independent and never scan unloaded terrain.

Priorities for this pass are missing square beach pads, continuous shallow
seabeds, retained cliff mass, complete rounded arch ends, visible thick shelf
overhangs, and pool water retention. Also check trees on sand, abrupt vegetation
changes, ordinary rivers/lakes being mistaken for coasts, and fresh-generation
responsiveness. Attempt counters are not unique feature counts or guaranteed
spawn frequencies.

## Diagnostics and verification

Audit terrain format 5 identifies physical-coast planning and counts water by
fluid tag, including modded water states. It distinguishes biome coordination
being enabled, the coordinate hook actually being observed, the chunk biome-fill
hook being observed, and selections actually being changed. A newly generated
eligible world should observe both hooks; reopening already generated chunks
does not require new fill calls. Density-node activity and sampling failures remain
separate. The original diagnostic field names are retained for audit tooling;
coastal distance and relief now describe physical geometry rather than a
nearest biome anchor.

Geometry and registry tests check deterministic planning, physical margin
continuity, complete feature bounds, protected biome selections, unchanged
source serialization, unload cleanup and pool handling. The
[runtime validation report](terrain10-runtime-validation.md) records stored-biome,
save/reopen and generation-order checks against the pinned stack. Automated tests cannot establish
the visual naturalness or feature frequency of the full customized client pack.

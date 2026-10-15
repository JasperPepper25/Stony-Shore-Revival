# Terrain.11: varied coastal forms and solid interiors

Version **0.8.0-terrain.11**, Minecraft 1.20.1 / Forge 47.2.0 development build.
Supported native terrain stack: Tectonic 3.0.17, Terralith 2.5.4 and
Lithostitched 1.4.11, standard Overworld, sea level 63, without WWOO.
The surface biome adapter targets TerraBlender 3.0.1.10 when present.

## Changes from V10

- Ordinary coastal shaping intersects the actual upstream density at every
  height. A smoothed height estimate no longer manufactures rock skins over
  exterior air. Sandy apron fills require a verified underlying foundation;
  pool liners remain bounded. Accepted finite fins and overhangs have solid
  interiors throughout their volume, rather than being clipped into shells.
- Arches have rounded and elongated rock-fin families, with varied length,
  height, taper, bend and coherent face weathering. Roof checks span the
  declining crown. Complete volumes and collision bounds cross planning cells.
- Overhang undersides sink into a deeper root buttress and ease toward the lip.
  Smaller, thinner lips coexist with the larger formations.
- Broad staggered cliff tiers change local height and slope along the shore;
  coherent pocket coves recess occasional headlands. These changes only lower
  the ordinary height profile and remain in the physical coastal corridor.
- Supported blind sea caves open to sea-level exterior water. Higher caves
  require an existing accessible shelf. Floor, roof, side and back probes reject
  unsupported rooms and sampled existing openings. Interiors use moss, cave
  vines and plants or dripstone. Sampled protected original river/underground
  biomes veto complete plans; the finisher also checks stored biome protection.
  Ordinary shoreline water retains its previous depth limit; deeper forced
  water is confined to accepted flooded rooms. This feature needs field review;
  sampled raw-density support is not proof that every small final-block void
  is sealed after the entire modpack finishes generation.
- The rare upper size tail of elevated pools can produce larger basins.
  Existing small/medium pool dimensions, attempt limits and seeded candidate
  locations/salts remain. Accepted frequencies can change with terrain fit and
  the new formations' collision footprints.
  All sizes retain terrain-fit and containment checks.
- Our surface decorations now also run on eligible coastal beaches. Keeping
  `minecraft:beach` retains the pack's exact-biome palm rules. Coastal sandy
  tide pools are fully checked before excavation. Optional Quark logs use
  registry lookup and safe absence fallback.
- Recognized natural Terrain Slabs dirt, grass, gravel and stone steps in
  planned sandy columns become `stonyshorerevival:coastal_sand_slab`, preserving
  half-height and waterlogging. This vanilla SlabBlock uses the sand texture
  and has no falling-block behavior. Vanilla building slabs, unknown IDs and
  double slabs are retained; recognized Terrain Slabs states inside the verified
  sandy cap are eligible. Disk and ore conversion can reset the Terrain Slabs
  generated flag. In planned sandy coverage, generated slabs can qualify without
  adjacent sand; a false or reset flag requires physical sand context.

## Settings

These independent toggles default to true under `[generation]`:

```toml
coastal_cliff_tiers = true
coastal_embayments = true
coastal_caves = true
```

They require `coastal_landforms`. Existing arch, beach, tide-pool and biome
coordination settings remain available. Restart after configuration changes.
Unsupported terrain layouts suspend the native adapter and are reported in
the audit. These changes affect newly generated chunks only.

## Install and compare

1. Close the game and replace V10 with
   `stony-shore-revival-0.8.0-terrain.11-srg.jar`; keep one Stony Shore Revival
   JAR. Use the same version on a server and its clients.
2. Create a fresh test world with the same pack settings and a known seed.
   Existing generated terrain is not repaired. The submitted audit omits the
   world seed; its filename suffix is not a confirmed seed.
3. Check loaded terrain from above and below. Include a shader-free view and
   avoid judging unfinished Distant Horizons terrain as generated blocks.
4. Inspect beach palms, grass, boulders, logs and tide pools; submerged sand
   steps; both arch families and thick piers; small and large overhang roots;
   variable cliff benches; sea caves, higher shelf caves and their interiors;
   pool water, intact rims and the rare larger basins.
5. Use `/stonyshore mark <label>` over each example, then `/stonyshore audit`.
   One archive supports up to 16 saved sites. Keep separately labeled successes
   and defects, including side/back cave openings and any remaining floating
   blocks or repeated slit patterns. `/stonyshore record start` and `stop` can
   retain bounded generation history.

The delivery validation records pure geometry tests, Minecraft-dependent unit
tests, production-JAR checks and isolated terrain-stack runtime checks. The
isolated stack does not include the complete customized pack, Terrain Slabs,
Quark, the vegetation mods or shaders. It cannot establish visual quality,
feature frequency or every cross-mod finishing interaction.

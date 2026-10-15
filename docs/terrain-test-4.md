# 0.5.0-terrain.4 — local shelves, pool placement and beach compatibility

Replace terrain.3 with the terrain.4 `-srg.jar`, keep one Stony Shore Revival JAR,
restart and test a fresh world. Existing chunks are not repaired. Pack guards and
restart-required settings remain unchanged. `coastal_landforms=false` retains the
older low-coast path; `coastal_arches=false` disables arches. Overhangs belong to
regional landforms. WWOO remains excluded.

## Findings from terrain.3

The October 3 15:23 audit confirms terrain.3, Terrain Slabs 4.1.1-beta, Tectonic
3.0.17, Terralith 2.5.4 and Lithostitched 1.4.11. The adapter is installed, reports
no sampling failure and remains wrapped. Audit export completed without errors.
The sample at X=1549/Z=-3584/Y=138 contains 1042 ocean and 47 stony-shore columns;
only two plans are active, all water planes are sea level, and there are no arch
candidates. Its Y=106..154 block band is entirely air, so it cannot measure the
pictured low pools or establish overall arch/pool frequency.

Screenshots show continuous ledges, narrow tall remnants, bare stone slabs and
ore interruptions on sand. Source confirms fixed 16-block terrace bands, sparse
pool searches requiring pre-existing near-flat footprints, and sand caps that
reject both ores and slabs. The 8-block surface-search stride could miss thin
upper rock; the altitude fade then restored original density above that estimate.
That is a plausible contributor to floating/jagged remnants, not proof that every
visible spike has the same cause.

## Changes

- Remove global terrace-height bands. A continuous coast profile retains areas
  of higher cliff; compact regional shelf plans create staggered ledges at locally
  chosen elevations. Regions are 64 blocks across, but shelf footprints occupy
  only part of each region and have varied axes/orientation/irregular edges.
- Plan the shelf and elevated basin together, with up to 16 candidate locations.
  Require a protected biome footprint and solid original floor/rim, but do not
  require an already-flat terrace. Each accepted basin has one water plane and
  a 2–4-block core. Complete footprints stay within their planning region while
  crossing Minecraft chunk boundaries normally. No filled volume is raised into
  pre-existing empty space. Carving remains bounded relative to original terrain.
- Scan every vertical block at cached four-block surface nodes and interpolate
  between them. Smooth high-surface biome exclusions. Do not restore original
  high rock above an active regional cut; the legacy low-coast path is unchanged.
- Try arches at more positions/orientations, accepting low planned coastal portals
  while retaining original roof/pier checks. Taper the reserved roof into each
  portal so it cannot seal the newly opened ends. Arch placement is still conditional.
- Add occasional one-sided cliff recesses with a checked rock roof, creating
  overhangs where a through-arch cannot fit. Recesses and shelf pools use separate
  regional sites so a recess cannot cut through a planned basin floor.
- Extend sand onto adjacent ocean floors within 32 blocks of selected beaches,
  retaining stronger cover through the first six blocks below sea level and
  fading to zero at 24 blocks below it. Ocean density is unchanged.
- Allow natural ore-tagged blocks in the three-layer beach cap. This deliberately
  removes covered ores in selected beach caps, without changing deeper ore veins
  or ore rules elsewhere. Unsupported surfaces and block entities remain excluded.
- Recognize generated Terrain Slabs stone-family bottom slabs, finish their ground
  as sand and convert them to `terrain_slabs:sand_slab`, preserving shared state
  such as waterlogging and the generated marker. Player/building slabs are excluded.
  If Terrain Slabs runs afterward, it instead sees the already-finished sand.
  This is optional registry-based compatibility, not a global slab-map replacement.

Terrain Slabs source checked at commit `db1e555a69b302920223339a580036806cf30619`:
`generation/SlabFeature.java`, `registries/ModBlocksRegistry.java`, and
`block/customslabs/specialslabs/CustomSlab.java` in
https://github.com/Coun7ered/terrain_slabs_multiloader/tree/1.20.1 .
The installed pack interaction still requires testing; source inspection alone
is not proof that every runtime feature ordering has been exercised.

## Diagnostics and testing

Audit format 5 retains the camera-relative sample and adds a second sample for
**each measured ocean-floor height**, from four blocks below to twelve above.
This keeps the ground/water visible in the audit when the camera is high overhead.
It also records overhang candidates and runtime planning counters for accepted
shelves, pools, arches and overhangs, plus several rejection categories. Counters
are planning evaluations/cache misses, not unique generated structures.

Please compare shelf span/height variation, actual elevated water, low-pool rim
smoothness, sand depth, sand slabs, exposed beach ores, arch/overhang openings,
and chunk-generation speed. Run `/stonyshore audit` near a remaining problem.

Synthetic checks exercise varied elevated water levels, supported and rejected
basins, intact region edges, through-arch versus blind-cliff sites, overhangs,
thin upper rock, deterministic traversal and beach apron limits. They do not
simulate the entire pack. Raw source density differs from interpolated terrain;
later mods can change the final blocks. Visual quality and full-pack performance
remain the purpose of this test build.

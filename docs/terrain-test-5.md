# 0.5.0-terrain.5 — coastal placement and sampling test

Replace terrain.4 with the terrain.5 `-srg.jar` and restart in a fresh disposable
world. Keep exactly one Stony Shore Revival JAR. Already generated chunks do not
change. Keep the audited Tectonic/Terralith/Lithostitched combination without WWOO.

## Evidence from the October 5 test

Seven format-5 audits sampled stony shores near X=1164..2484/Z=36..537. Six
local samples show only the sea-level planned water plane; the seventh has eight
sampled columns at water level 72. Its floor-band block palette actually contains
source water at Y=69..71, so elevated filling can work where a plan exists.
Across the running session, 174 of 2616 evaluated regional sites accepted a
shelf and pool. 2980 candidate shelves were rejected by terrain/biome shape, and
150 by support checks. None of 32208 tested arch orientations was accepted;
21 overhang plans were accepted, but none appears in the seven local samples.
These cumulative counters include cache misses and evaluations outside the seven
sample footprints. They are not counts of distinct generated structures.

The seven ZIPs have one repeated, nonfatal `HolderHolder` serialization warning
for `minecraft:overworld` noise settings; the runtime status and local block
observations are present. The latest.log also shows long Streams Reflowing waits
and overloaded ticks. Performance remains a required pack-level check.

## Changes to check

- Sample the original height in eight-block bands, refining the highest detected
  solid band block by block. Avoid regional planning entirely for non-shore
  columns in the density/water hooks. The active cut still does not restore
  isolated high rock above a planned coastal cut.
- Apply the low tidal relief field to cliffs that the regional coast profile has
  already lowered to the coastal band. Keep the checked rock floor and the
  existing sea-level water rule.
- Permit smaller, irregular elevated basin footprints on narrow ledges and relax
  conservative support checks while retaining a solid floor/rim and a single
  water plane. Existing large eligible footprints remain possible.
- Select beaches from a broader, still sporadic noise band, and extend selected
  beaches farther into the shore. Replace per-block beach-edge dithering with a
  smooth field shared across chunk borders; sand still continues into the ocean.
- Loosen portal elevations for through-arches without allowing blind solid-ended
  tunnels. Try more supported recess sites for overhangs, preserving their roof
  checks. Audit counters now distinguish failed arch piers and roofs from portals.

Run `/stonyshore audit` near a new elevated basin, near a low stony pool, and at a
sandy shore edge. Include screenshots and coordinates. Look for source water on
high basins, several sea-level pools, naturally tapered sand/water edges, and any
arch or overhang openings. If arches are still absent, the new rejection counters
will tell us which physical precondition excludes them. Compare chunk load times
with terrain.4 using fresh areas in a copied test world; this optimization has
not yet been measured in the full modpack.

# terrain.12-perf.3 experimental ground-cache optimization

Fresh coastal exploration in the perf.2 capture improved noise and biome-stage
timings, but recorded ground-cache monitor waiting increased to 33.39 cumulative
worker-seconds. Almost all recorded shore-cache acquisitions waited at shared
lookups, rather than publication after planning.

## Change from perf.2

Only the native model's ground-plan cache changes its storage algorithm. Height,
detailed-column, cave, region and biome-policy caches keep their perf.2 behavior.

- Shared ground hits read a volatile, immutable bucket snapshot without entering
  a publication monitor or updating shared LRU links.
- Each full primitive coordinate key has two candidate placements. Spare slots
  are balanced between them; when both are full, the older publication is replaced.
  A hit does not reorder entries. Full key comparisons prevent hash collisions
  from returning another coordinate's plan.
- Shared storage remains bounded to **8,192 entries** across 1,024 buckets of up
  to eight entries. The fixed eight-value worker front is unchanged. Worker fronts
  can retain deterministic values after shared eviction, as in perf.2.
- Misses invoke a factory bound once to the model, decoding the original signed
  X/Z coordinates from the packed key. Hits no longer construct a capturing ground
  factory lambda. Ground planning arithmetic is carried forward unchanged.
- Planning occurs outside all monitors. Publication rechecks both placements
  while holding their monitors in ascending index order, so nested planning cannot
  wait on a factory holding a bucket lock and opposite placements cannot invert
  the publication lock order.

Each published snapshot array and its entry key/value fields are immutable. Old
arrays remain valid for readers until discarded. The fixed worker-front state
contains keys and completed values, without cache/factory/model references.
The factory is owned by the model's cache and is not stored in the worker front.

Replacement differs from perf.2's segmented LRU. It can change recomputation
frequency; a faster lookup alone is not evidence of faster Minecraft generation.
Small-cache, collision, concurrent-eviction, monitor-independent reads, nested
factories, duplicate-publication and per-instance isolation checks cover that API.
The project CI runs these with the existing terrain, biome and lifecycle suite.

## Diagnostics

The existing audit includes `groundCacheHits`, `groundCacheMisses`,
`groundCacheLocalHits`, `groundPlanningNanos` and these new fields:

- `groundCacheSharedReadHits`: hits served by either shared snapshot placement.
- `groundCacheEntries`, `groundCacheCapacity`, `groundCacheBuckets`: retained
  shared entries and their fixed storage limits.

`groundCacheRecentHits` now means hits in a bucket's most recently **published**
slot. In perf.2 it meant the most recently **accessed** entry of a stripe. Compare
total/local/shared hits and misses rather than equating those two recent-hit paths.
Counters are cumulative for the current world model; an audit is not an automatic
before/after snapshot of just the profiling interval.

## Test protocol

Install only the perf.3 SRG JAR in the test instance. Use a fresh world with the
same baseline seed/preset, all mods enabled and `coastal_biome_coordination=true`.
Keep JVM/memory settings, Distant Horizons, visual mods, render/simulation
distances, movement speed and compatibility settings as in perf.2.

Use your normal settling procedure, noting any substantial change in time since
launch or world load. Start at X=-467, Z=679, follow the same coastline west then
south and finish at X=-1029, Z=940. Stay unpaused at the endpoint until the
420-second v1.2 collector run ends. Cancel Spark before recording and request
zero thread dumps.

The existing perf.2 reference is `ATM9-capture-20261014-013636-b89a28.zip`, world
`Perf12 Perf2 Candidate`. After the new collector finishes, run `/stonyshore audit`
and attach its separate ZIP from the instance's `stonyshore-audits` directory,
along with the performance capture and any visible terrain/water differences.
The audit itself samples terrain and exports resources, so keep it outside the
timed recording. If session state or workload differences make the comparison
ambiguous, a fresh perf.2/perf.3 pair can follow.

The branch remains experimental. The installable artifact must come from a
successful full Forge build with the Java 17 target and SRG remapping. Exact
synthetic terrain comparisons support the tested fixtures; they do not establish
whole-pack block/biome/river equality or predict an in-game percentage gain.

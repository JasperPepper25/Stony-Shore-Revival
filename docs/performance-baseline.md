# Performance development baseline

Accepted on 15 October 2026 UTC (14 October EDT), after the perf.3 comparison.

## Source and branches

The accepted runtime implementation is **0.8.0-terrain.12-perf.2** at commit
[`3db00db49392fa303629f4beaaf7a1da1b0e48f6`](https://github.com/JasperPepper25/Stony-Shore-Revival/commit/3db00db49392fa303629f4beaaf7a1da1b0e48f6).

| Reference | Purpose |
| --- | --- |
| `baseline/terrain12-perf2` | Keep at the exact tested perf.2 commit as a comparison checkpoint. |
| `main` | Accepted development baseline plus its documentation; start new refinements here. |
| `terrain-rewrite` | Terrain development line promoted to the same perf.2 implementation. |
| `perf/terrain12-cache` / draft PR #3 | Preserved perf.3 ground-cache experiment; not selected as the baseline. |

The baseline promotion changes branch selection and documentation only. Runtime
source, resources, tests, build configuration and `mod_version` remain those of
the tested perf.2 commit. Future candidates should branch from the accepted
development line and compare against the named checkpoint. Do not advance the
checkpoint when creating a candidate.

## Tested artifact and automated validation

- File: `stony-shore-revival-0.8.0-terrain.12-perf.2-srg.jar`.
- Size: **263,258 bytes**.
- SHA-256: `95a1d72d43cc198a7148409cad052ae0a17dd5fb363aadee3691932e93726e56`.
- Successful original build: [GitHub Actions run 38506401092](https://github.com/JasperPepper25/Stony-Shore-Revival/actions/runs/38506401092).
- Original test results: **181 tests across 31 suites, with zero failures,
  errors or skips**. Rebuilds can have different archive hashes; the hash above
  identifies the specific JAR used in the accepted capture.

Perf.2 uses primitive coordinate caches, fixed worker fronts, reusable native
height stencils and cached horizontal biome geometry policies. Positional biome
queries and temperature/tag/depth/river/cave checks remain live. Landform
formulas and the existing terrain-stack compatibility boundaries are retained.

## In-game evidence behind this decision

Reference capture: **ATM9-capture-20261014-013636-b89a28.zip**, world named
`Perf12 Perf2 Candidate`. The world name does not make this a future candidate;
it now serves as the accepted perf.2 measurement reference.

Against perf.1, perf.2 had 14.3% lower matching noise-stage mean duration,
22.8% lower biome-stage mean duration, and reached 90% of 1,831 shared FULL
coordinates 31.4 seconds earlier. These are results from one controlled route,
not a general percentage guarantee for the whole pack.

The subsequent perf.3 capture was
**ATM9-capture-20261014-225719-8ba066.zip**, with the post-capture audit
**shore-audit-20261015-030535-2382457190095034996.zip**.

| Perf.2 / perf.3 comparison | perf.2 | perf.3 |
| --- | ---: | ---: |
| Time to 90% of the same 1,969 FULL coordinates | 195.010 s | 231.590 s |
| Recorded ground-cache waiting, cumulative workers | 33.391 s | 0.574 s |
| Recorded detailed-column cache waiting, cumulative workers | 13.736 s | 24.118 s |
| Matching biome-stage mean | 118.52 ms | 137.65 ms |
| Matching noise-stage mean | 336.27 ms | 323.51 ms |

Perf.3 achieved a 98.3% reduction in recorded ground-cache waiting but reached
the common completion threshold 36.580 seconds later, or 18.8% more elapsed
time. Its noise-stage mean advantage was only about 0.2% after excluding work
started during the first minute of either recording. This supports keeping
perf.2 as the best measured development reference rather than automatically
accepting the latest experiment.

There is one recording per build, and the sessions have different warm-up/world
reload histories, heap occupancy and competing Streams sampling activity.
That limits causal attribution of the slower perf.3 result. It does not prove
that its ground-cache implementation is intrinsically slower, and the
experiment is preserved for investigation.

Stage durations include scheduling/dependency delays; they are not exclusive
CPU time. Monitor waits are accumulated across workers above JFR's recording
threshold, not seconds directly added to the route. Match chunk coordinates
and completion cohorts when comparing results.

## Controls for future comparisons

- Keep the baseline seed/preset, movement method/speed and coastal route fixed:
  start X=-467, Z=679; travel west then south; finish X=-1029, Z=940.
- Use a newly created test world for each run, fully close Java between runs,
  and keep the pre-route wait after spawn is ready the same.
- Keep all other mods and captured settings unchanged. The current pair has
  666 manifest entries, 45 identical captured config files, render/simulation
  distances 14/14, coastal biome coordination=true, Java 25 and ZGC.
- Retain ModernFix `mixin.perf.release_protochunks=false` in this test setup.
- Use the v1.2 seven-minute collector, with periodic full thread dumps off.
- Run `/stonyshore audit` after recording ends. Audit counters are lifetime
  snapshots, not seven-minute deltas; avoid treating them as timed miss rates.

## Next refinement targets

1. Investigate the detailed-column shared hit path and repeated factory
   allocation. In perf.3 it accounts for about 76% of recorded shore-cache waits.
2. Add lightweight start/end cache and planner counter snapshots tied to the
   recorded interval and model identity before extending replacement policies.
3. Fix the audit's partial overworld noise-settings export. The observed
   `HolderHolder` codec exception occurred after profiling; successful counters
   and local pool checks remain useful.
4. Preserve terrain geometry, water ownership, biome protections and
   Tectonic/Terralith integration. Judge candidates by matching-chunk completion
   as well as isolated cache statistics and deterministic/concurrent checks.

This is a selected **development baseline**, not a declaration of a final
release or proof of all-world compatibility.

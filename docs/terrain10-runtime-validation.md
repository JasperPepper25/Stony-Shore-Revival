# V10 runtime validation — 2026-10-10

Candidate: **0.7.0-terrain.10**, Minecraft 1.20.1 / Forge 47.2.0.
Seed: `7340382191261946640`, matching the V9 field audit.
Installation and visual priorities are in [the test guide](terrain-test-10.md).

## Scope

An isolated Forge development GameTest server used Tectonic 3.0.17,
Terralith 2.5.4, Lithostitched 1.4.11 and TerraBlender 3.0.1.10. The harness
generated 27 chunks across three selected coastal neighborhoods, saved and
reopened that world, then generated those chunks in reverse request order in
a second fresh world. Each phase used identical main bytecode: 92 classes.
Source hashes were stable throughout each launch.

The development launch used official-name remapped copies of those exact
production mod JARs and refmaps. It exercised the real noise generator,
biome source and chunk palettes. The harness stays outside the shipped mod.
Production SRG packaging is checked separately by the final build verification.

## Results

All three phases passed the following checks:

| Check per phase | Result |
| --- | ---: |
| Stored quart-grid biome cells matching live selection | 12,528 |
| Surface and upper biome checks matching live selection | 1,296 |
| Adjusted cells actually stored within the quart grid | 1,404 |
| Adjusted surface and upper samples | 198 |
| Stored biome mismatches | 0 |
| Submerged ocean variant samples preserved | 288 |
| Changed deep biome samples | 0 |
| Original positional lookups matching TerraBlender exactly | 1,296 |

Fresh forward and reverse worlds each observed 994 chunk biome-fill calls.
The reopened world needed no new fill calls. Integration stayed installed with
no sampling failures. Exported transformed bytecode confirmed that the coastal
coordinate callback precedes TerraBlender's cancelling callback and that the
generator invokes exactly one coastal biome-fill redirect while retaining its
upstream cached climate sampler.

Both comparisons against the fresh forward world matched all 27 sampled
coastal-plan/biome fingerprints and all 27 sampled block fingerprints exactly.
There were zero changed sampled cells after reopening or reversing request
order. Fingerprints sample chunks; they are not exhaustive whole-chunk equality
proofs.

## Timing observations

| Phase | Spawn preparation | Request 27 test chunks | Audit sampling |
| --- | ---: | ---: | ---: |
| Fresh forward | 50.309 s | 6.054 s | 0.548 s |
| Saved-world reopen | 1.365 s | 0.188 s | 9.370 s |
| Fresh reverse | 48.990 s | 5.952 s | 0.546 s |

Reopening rebuilds in-memory coast caches; its first audit is therefore cold.
Fresh generation includes upstream noise, structures, surface building and
decoration. These are development-server observations, without a disabled-mod
control, rather than measured overhead or a full-pack throughput benchmark.
Aggregate planning timers include nested and potentially parallel work, so
they cannot be divided by chunk wall time to obtain a cost percentage.

## Remaining checks

This reduced stack has one TerraBlender parameter tree and does not include
Biomes O' Plenty, Regions Unexplored or Oh the Biomes We've Gone regional
registrations. Their full-pack interaction, untagged cave biomes, vegetation
transitions, visual naturalness and arch/overhang frequency remain fresh-world
in-game checks. These results establish that the bounded coastal policy runs,
writes the intended biome samples and survives reopening in the pinned stack.

The separate validation artifacts include phase reports, transformed bytecode,
class hashes, source-stability records and original/development dependency
provenance. Implementation lessons are recorded in
[the research follow-up](research/worldgen/terrain10-implementation.md).

# V10 implementation decision — 2026-10-10

Baseline: V9, `c61270e946adcf06f57ff60c82d90cb3303271b2`.
Candidate: `0.7.0-terrain.10`, on the terrain rewrite development branch.
Dependencies and installation are recorded in [the V10 test guide](../../terrain-test-10.md).

This is an implementation follow-up to the source research. It does not revise
the notebook's pinned upstream findings or claim that their visual results have
been reproduced.

## Decision

Use one physical coast description for terrain, local feature placement,
materials and surface biome selection. Preserve the underlying Tectonic /
Terralith landscape and TerraBlender regional lookup. Test local coastal biome
extent before attempting a global climate or continent rewrite.

V9 feedback showed square beach pads, clipped-looking arch ends, acceptable
elevated pools and few visible overhangs. The working hypothesis is that biome
boundaries and discrete shore anchors were poor substitutes for the physical
waterline. Extra independent noise would not resolve that disagreement.

## What changed

- Reconstruct the immutable final upstream density field on a fixed lattice.
  Extract ocean-connected waterline segments and blend nearby cliff relief.
  Build tiles with overlapping halos; cache points, tiles, distances and plans.
- Share signed coastal distance between the beach toe, shallows, material
  selection and surface biome band. Preserve the cliff attachment behind
  accepted added formations. The land corridor is configurable from 48 to 160
  blocks; the offshore envelope is 96 blocks.
- Keep complete arch and overhang volumes across local mask boundaries. Round
  the outer arch support in three dimensions and select overhang roots from a
  real drop rather than requiring a cavity at a random inland position.
- Preserve the original source codec and possible-biome set. Obtain the original
  coordinate biome lookup under a per-thread guard, preserving TerraBlender's
  positional regional selection. A once-per-world zero-depth climate sampler
  supports surface classification without modifying the world's climate noise.
  Wrap the chunk biome-fill resolver as well: its upstream lookup keeps the
  supplied cached sampler, while coastal policy uses the canonical world binding.
  Existing blended chunks bypass coordination; unload removes that binding.
- Preserve V9 pool construction/tuning and improve audits of physical planning,
  hook observation, actual biome changes and modded water states.

## Checks and limits

Unit tests cover planning order, feature bounds, physical margins, dry barriers
between lakes and ocean, protected biome choices and unchanged serialization.
Separate validation artifacts record compilation, production remapping and
isolated runtime checks against the pinned terrain stack.

Coast reconstruction is an approximation: 16-block height nodes and waterline
cells, interpolated distance on an 8-block lattice. Tiny coves and thin upper
rock can be missed. The first eligible tile performs many upstream probes;
runtime timing should guide later optimization. Cache evaluation counts are not
unique placements. Runtime checks of a reduced terrain stack are not a complete
ATM9 compatibility or throughput benchmark.

Keep/revise/reject remains pending the user's fresh-world visual pass. Compare
the V9 audit seed at the beach pads and arch site, plus new overhang sites.
Record pool water after reload, vegetation at the new biome edge, distant-ocean
preservation and generation responsiveness before broadening climate changes.

## Runtime lessons from biome coordination

Two checks exposed integration gaps that policy unit tests and a successful
server launch did not reveal:

- **Cancelling HEAD callbacks need actual order verification.** The pinned
  Forge/Mixin transformation initially placed our coordinate callback after
  TerraBlender's early return. Exported bytecode proved the callback was
  unreachable. Biome callback priority 900 puts it ahead of TerraBlender's
  priority 1000 in this pipeline. The existing density priority remains 1100;
  these are separate integration points. Do not infer runtime order from the
  numerical priorities alone.
- **Live queries are insufficient evidence of saved biome placement.**
  Minecraft 1.20.1 `NoiseBasedChunkGenerator.doCreateBiomes` passes
  `NoiseChunk.cachedClimateSampler` to `ChunkAccess.fillBiomesFromNoise`.
  This sampler differs from the canonical `RandomState.sampler()` identity.
  The first live-query smoke showed 1,404 adjusted cells, but all 1,404 were
  still original in the stored chunk palette. A separately created diagnostic
  world reproduced the same result, ruling out old generated chunks.

The compatibility contract must cover the original regional lookup, the policy
decision, the generator's write path and persisted chunk palettes. Keep the
exact supplied cached sampler for the upstream lookup; use the canonical world
binding for the coastal policy. Preserve scoped delegate guards, original
codecs, possible-biome membership and old-chunk blending. Verify saved cells
after generation and reopening, alongside direct lookup tests.

The corrected build passes these checks, including saved palettes and reversed
generation order. See the [V10 runtime validation report](../../terrain10-runtime-validation.md)
for the sample counts and limits.

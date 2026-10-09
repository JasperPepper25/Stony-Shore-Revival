# World generation research for Stony Shore Revival

Reviewed **2026-10-09** for the project's **Forge / Minecraft 1.20.1** coastal terrain work. This is a source-based research notebook, with an overview for readers and detailed implementation references for developers.

The implementation baseline is [V9, commit `c61270e9`](https://github.com/JasperPepper25/Stony-Shore-Revival/tree/c61270e946adcf06f57ff60c82d90cb3303271b2), currently undergoing in-game testing. This notebook is published on `main` for easy reference; it does not merge that development build or change terrain code.

## Main conclusions

1. **Naturalness needs coherent relationships between features.** Broad coastal shape, cliff relief, beach width, shallow seabed, and landmark orientation should share a stable coastal description. Adding independent detail noise cannot reliably fix competing shelves or rectangular masks. Tectonic, Larion, and UltraTerraForged offer useful mechanisms for broad form, bounded deformation, and profile transitions. [Terrain and climate review](terrain-and-climate.md), [coastal profiles and procedural methods](ultraterraforged-and-procedural-methods.md).
2. **There are two strong arch references.** Standalone Terralith combines implicit arch density with terrain. BWG adds curved rock volumes between embedded anchors, using chunk-distributed structure pieces. Neither reviewed implementation guarantees our required ocean direction and cliff attachment; those remain our responsibility. [Terralith density](terrain-and-climate.md), [BWG construction](biomes-and-features.md).
3. **Compatibility is specific composition, not just successful loading.** Tectonic's Terratonic overlay replaces density paths, including the standalone Terralith arch contribution. Lithostitched supplies a pre-seeding router wrapper that V9 already uses. Resource priority, fluids, surfaces, and later features still require validation. [Combined terrain graph](terrain-and-climate.md), [integration contracts](integration-and-tooling.md).
4. **Biome labels and physical feature space can be separate.** TerraBlender weights and climate mappings affect selection; they do not guarantee a wider stony cliff. Bounded geometry can extend into compatible ocean while retaining its biome, provided water, materials, and feature footprints follow that geometry. Continue evaluating V9's approach before changing the biome map. [Biome systems](biomes-and-features.md), [options and consequences](coastal-experiments.md).
5. **Visible pools need an end-to-end diagnosis.** Proposal frequency, support rejection, overlap arbitration, realized basin, retained water, and final decoration are different stages. Geophilic's stony-shore puddles and moss are a concrete interaction test. Raising attempt counts alone could conceal the real failure. [Feature review](biomes-and-features.md), [pool experiment](coastal-experiments.md).
6. **Optimization should begin with measurement.** Promising patterns include stable upstream probes, cached site plans and negative results, cheap bounds checks, per-column reuse, and sparse 3D work. No benchmark here establishes that another noise library or generator is faster than V9. [Engine and tooling review](integration-and-tooling.md), [measurement protocol](coastal-experiments.md).

These conclusions are our interpretation of the inspected evidence. V9 feedback and controlled experiments determine what enters the next build.

## Reading map

| Chapter | Contents | Read this for |
| --- | --- | --- |
| [Terrain density and climate](terrain-and-climate.md) | Tectonic, Terralith, their combined graph, Larion | Noise composition, arches within density, warping, climate layout, installed-resource comparisons |
| [Biomes, surfaces, and features](biomes-and-features.md) | TerraBlender, BOP, RU, BWG, Geophilic | Biome placement, sand/rock ownership, procedural structures, decoration interactions |
| [Integration and tooling](integration-and-tooling.md) | Lithostitched, Terra, Iris, More Density Functions, Density Function Editor, OTG | Safe integration stages, exposed surface spans, cache alternatives, inspection tools |
| [UltraTerraForged and procedural methods](ultraterraforged-and-procedural-methods.md) | Coastal shaping, shallow shelves, sea caves, shelters, primary papers and geology | Shared profiles, implicit local geometry, site distribution, process-based design |
| [Experiments for our coast](coastal-experiments.md) | Original proposals tied to V9 and the user's visual references | Priorities, acceptance checks, pool diagnostics, performance and compatibility testing |

## Project comparison

This table summarizes the reviewed mechanism, not every capability of each project. Exact commits and source paths are recorded in the linked chapters.

| Project | Reviewed version or scope | Most useful lesson | Main boundary |
| --- | --- | --- | --- |
| Tectonic | Installed 3.0.17; labeled comparison with 3.0.25 source | Broad terrain families, splines, aligned detail, density caching | Whole terrain graph; paired overlay differs from standalone |
| Terralith | Installed 2.5.4 relevant assets and pinned `1.20` source | Climate-gated implicit arches and biome parameter boxes | Standalone arch path replaced in the reviewed Terratonic overlay |
| Larion World Generation | 4.3.1 source, MC 1.21.1 | X/Z deformation that can vary with height; coordinated fluid fields | Different game version; author warns against Tectonic/Terralith combination |
| Lithostitched | Exact V9 1.4.11; separate modern 1.8.0 comparison | Pre-seeding router composition and explicit markers | Modern biome injector APIs are absent from 1.4.11 |
| TerraBlender | Pinned `1.20.1` branch | Region-family selection, climate lookup, namespace surfaces | Region weight/scale does not guarantee shore width |
| Biomes O' Plenty | Pinned `1.20.1` branch | Coastal climate slices, separate materials and placement predicates | Branch snapshot is not proof of an installed release |
| Regions Unexplored | Historical Forge 0.5.6 source; documented modern split | Custom coast identities, embedded/tapered rock shapes | Historical TerraBlender and modern Lithostitched implementations differ |
| Oh The Biomes We've Gone | 1.8.2-SNAPSHOT source for MC 1.20.1 | Procedural additive arch geometry and serialized chunk pieces | No reviewed ocean-facing cliff-anchor rule; source is ARR |
| Geophilic | Pack 3.7 base and generated 1.20 resources | Small vanilla rock/puddle features; later surface interaction | Biome JSON composition needs version-specific compatibility |
| UltraTerraForged | Pinned `1.20.1` branch | Continuous coast/cliff/apron profiles, site validation, bounded cave work | Whole-generator architecture, not a verified add-on to our stack |
| Terra / Iris | Pinned source snapshots | Volumetric shaping, lazy evaluation, surface spans and bounded caches | Conceptual engine comparisons, not dependency recommendations |
| More Density Functions / Density Function Editor | MC 1.20.1 MDF 2.3.0; pinned editor source | Prototype vocabulary and cross-section inspection | Seed/cache lifecycle and custom-node preview need validation |
| OpenTerrainGenerator | Historical `1.16.4` source | Parameter blending and explicit object transition footprints | Historical architecture; cardinal smoothing can create straight outlines |
| FastNoiseLite / FastNoise2 | Pinned FNL Java source; FN2 documentation lead | Warping/noise vocabulary and alternative evaluation designs | No measured Java/modpack speedup; FN2 is not a Java drop-in |

For non-mod research, the procedural-methods chapter covers **Terrain Amplification with Implicit 3D Features**, **hydrology-based terrain**, **tectonic uplift/fluvial erosion**, **Poisson disk sampling**, NVIDIA's **3D density terrain** chapter, and **National Park Service coastal geology**. Reading depth is stated for each source, including abstract-only coverage where applicable.

## How to interpret the evidence

- **Observed / verified / source verified:** directly inspected code or resource at the named revision.
- **Author documented:** an official description, dependency listing, or compatibility statement; not an independent execution test.
- **Inference / interpretation:** our reasoning about relevance to this mod.
- **Proposal / experiment:** original future work, not implemented or validated by this notebook.

All chapters distinguish source mechanisms from visual outcomes and performance promises. Installed filenames establish presence, not effective load order. Source branches can include unreleased changes. Minecraft versions and loaders are part of every compatibility claim.

The notebook contains original analysis and source links. Public source is not blanket permission to copy implementation or assets; each chapter records the relevant reuse boundaries. Third-party source snapshots and paper PDFs used for local research are not redistributed here.

## Next decisions and future updates

Start with the [experiment chapter](coastal-experiments.md) after V9 testing. Prioritize remaining cliff/beach space conflicts and readable pools, then arch attachment and overhang silhouette. Profile the actual hot paths before changing the engine or noise library.

For each future experiment, append a dated decision with the baseline/candidate commits, exact dependencies and active resource order, hypothesis, fixed sites/seed, screenshots or slices, measured results, tradeoffs, and keep/revise/reject outcome. Keep those results distinct from source review. Update pinned references deliberately when upstream versions change instead of silently treating these notes as evergreen.

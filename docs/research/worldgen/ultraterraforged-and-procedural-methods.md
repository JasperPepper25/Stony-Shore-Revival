# UltraTerraForged, implicit terrain, and coastal process references

[Research index](README.md) · Reviewed 2026-10-09 · Minecraft target: Forge 1.20.1

## Scope and evidence

This chapter reviews actual source code and primary research. **Observed** means present in the pinned implementation; **interpretation** means a lesson for Stony Shore Revival; **proposal** means an experiment that has not been implemented or benchmarked here. Source review is not proof that every optional preset produces attractive terrain, or that another generator is compatible with our pack.

UltraTerraForged source was inspected at [commit `4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba`](https://github.com/Pandaismyname1/ultraterraforged/tree/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba), from its `1.20.1` branch. This is a snapshot of that branch on the review date, not a claim about all released JARs. It continues the TerraForged/ReTerraForged lineage. Its root [license](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/LICENSE) is MIT; adapted substantial code would require preserving the applicable notices. These notes contain original analysis and links, not copied implementation.

## 1. UltraTerraForged's terrain pipeline

### Ordered terrain, rivers, landforms, and climate

**Observed:** `Heightmap` separates continent evaluation, terrain-region selection, terrain height, river modification, landforms, and climate. `applyTerrain` calculates terrain types, applies the rivermap, then applies landforms and final labels. `sampleGround` deliberately omits rivers, climate, and landforms; `sampleTerrain` includes rivers and climate but omits landforms. Those cheaper, nonrecursive views let a landform inspect its surroundings without asking for its own finished output. [Heightmap source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/heightmap/Heightmap.java).

**Interpretation:** a stable upstream terrain view matters as much as the noise function. Our attachment and support probes should have an explicit contract: original terrain, coastal terrain before landforms, or final terrain. Accidentally mixing those views can create self-supporting projections, inconsistent feature acceptance, or expensive recursive planning.

`Landforms.make` assigns each category a seed even if that category is disabled, then establishes an explicit order. Ocean-floor shaping comes before coastal landforms; cliffs precede fjords; deltas follow the other coast changes. Turning one category off therefore does not shift the seed sequence of unrelated categories. [Landforms source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/landform/Landforms.java).

**Proposal:** document a similarly explicit ownership/order table for our beach toe, arch root, overhang, pool liner, water, and decoration. A shared coordinate alone does not make independently designed features agree.

### A continent field rather than a biome-shaped stencil

**Observed:** `CoastShaper` wraps the continent implementation and changes its edge/noise values. Warped Perlin noise creates bays/headlands. Deterministic grid sites locate peninsulas, islands, and spits; peninsula directions follow the negative gradient of the original continent field. Curved arms have tapered widths, rounded tips, and shallow surrounding shelves. Site acceptance checks for open sea ahead and surrounding land that could obstruct an arm. The changed field is then available to downstream terrain and climate. [CoastShaper source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/continent/CoastShaper.java).

**Interpretation:** this is a good example of making a coherent coastline before classifying its consequences. It is not a lightweight biome modifier. Porting its whole continent model would exceed the scope of a localized shore mod. The transferable idea is a shared coast coordinate and orientation, with complete feature footprints and site validation.

### Cliffs and beaches share spatial constraints

**Observed:** `SeaCliffs` selects coherent coastal stretches with a low-frequency field, measures proximity to open sea at 32-block grid corners, and interpolates it. It raises land near the shore, excludes rivers/lakes/wetlands/sand bars, and can preserve a low beach strip below suitable cliffs. Offshore stacks require nearby land and an appropriate cliff field. Important sharp areas receive an erosion mask. [SeaCliffs source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/landform/SeaCliffs.java).

**Interpretation:** blanket smoothing is not the same as naturalness. A coastline can have smooth plan-view contours and a sharply defined cliff face. Beach creation should reserve cliff geometry and use appropriate sites, rather than lower every coastal column to the same broad bench.

### Shallow ocean shelves are a separate profile

**Observed:** `Shelves` raises eligible sea-floor heights toward a profile running from approximately two blocks below water near shore to thirteen near the shelf edge. A low-frequency field varies the edge, and a transition blends back to the prior sea floor. Width is expressed in continent values, so it is not a constant physical distance in blocks. Rivers and lakes are excluded. [Shelves source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/landform/Shelves.java).

**Interpretation:** a submerged apron deserves its own depth and transition controls. Beach material selection, dry-beach width, and underwater slope should be related but separately tunable. Extending sand into the ocean does not by itself produce a gradual bathymetric transition.

## 2. Arches and overhangs: actual implementation and its limits

### Sea caves and arches

**Observed:** `SeaCaves` uses deterministic 40-block grid sites. It requires terrain seven to sixty blocks above sea level, searches eight directions for nearby low ground, and searches the opposite direction for the far side of a narrow headland. It then carves a path of ellipsoidal hollows near sea level, with a retained roof and water below the specified plane. Opening radius is 2.5–4.5 blocks. The search uses height relative to sea level; it does not prove that every low point is connected ocean. [SeaCaves source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/cave/SeaCaves.java).

**Interpretation:** this accepts existing geometry and can turn a suitable headland into an arch. It does not construct the large, outward-projecting fin in the user's reference. A similar carving approach would need stronger ocean-access tests and our own attachment/scale rules; it is not a reason to restore deep inland excavations.

### Rock shelters

**Observed:** `RockShelters` searches for a cliff foot within six blocks of a column, then erodes a short band while retaining at least a roof margin. Noise controls how far the lower rock is worn back and the height of the recess. Eligibility is badlands, plateau, or karst terrain. [RockShelters source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/cave/RockShelters.java).

**Interpretation:** a shallow undercut can make a pronounced overhanging cliff without making a deep cave. However, it only removes lower material; our desired ocean-facing shelf can also need added upper rock. Treat the reference silhouette, not a generic cave entrance, as the acceptance criterion.

### Carving stage, deterministic plans, and water ownership

`MixinNoiseBasedChunkGenerator` calls the custom cave system at the **tail of surface generation**. `CaveFeatures` applies an ordered list to the current chunk. This is a post-surface block-edit approach, distinct from composing our final density before surface generation. [Generator mixin](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/mixin/MixinNoiseBasedChunkGenerator.java), [CaveFeatures](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/cave/CaveFeatures.java).

`CaveCarving.hollow` and `line` clip their X/Z loops to the current chunk; the raw `clear` and `set` helpers require caller-supplied bounds. Its plan-time `terrainHeight` uses direct upstream terrain, independent of available neighboring chunks. Local carving may read actual heightmaps and cached tile information. Its clear operation preserves bedrock and existing fluids and explicitly selects air or water. [CaveCarving source](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/cave/CaveCarving.java).

**Proposal:** if testing a block-edit alternative, keep site planning independent of generation order, clip every write, and explicitly handle water, surface materials, decoration, heightmaps, structures, and later carvers. Density generation and block editing have different integration costs; neither is universally superior.

## 3. Performance techniques worth measuring

| Technique observed | Why it matters to us | Limit |
| --- | --- | --- |
| Per-landform, per-thread site cache; packed coordinates; negative-result sentinels | Avoid repeating a failed site search for every sample | Thread-local memory multiplies with workers; the cache clears after its limit rather than maintaining an LRU |
| Cached grid-corner measurements with bilinear interpolation | Move expensive neighborhood queries out of fine sampling | Coarse grids can lose narrow shores; bilinear derivatives can show grid patterns |
| Tile cache with asynchronous generation and chunk-use accounting | Amortize a larger terrain computation across chunks | Considerably more lifecycle and concurrency machinery than a small density wrapper |
| Shared column/cell sampling at quart-block coordinates | Reuse 2D descriptors across vertical queries | Four-block quantization can affect small features; world ownership and lifecycle still matter |

Sources: [SiteCache](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/landform/SiteCache.java), [SampleGrid](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/landform/SampleGrid.java), [TileCache](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/tile/TileCache.java), [CellSampler](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/densityfunction/CellSampler.java).

**Observed:** optional tile filters run droplet erosion and smoothing before required rim, steepness, beach, and postprocessing passes. The erosion implementation tracks a droplet's direction, sediment, water, and velocity and redistributes height using weighted brushes. This is actual iterative height-field processing, unlike a Minecraft climate parameter named `erosion`. [WorldFilters](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/tile/filter/WorldFilters.java), [Erosion](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/world/worldgen/tile/filter/Erosion.java).

**Interpretation:** regional erosion is a larger architectural option, not a cheap extra noise octave. For our local coastal layer, first measure descriptor planning, baseline probes, cache misses, and allocation. Only consider a regional solver if simpler profiles and bounded implicit landforms cannot meet the visual goal.

The reviewed `CoastsTest` locates landforms, compares enabled/disabled views, measures gained/lost land and connected components, and can render comparisons. These are useful behavioral checks; they do not certify visual quality or pack compatibility. They were inspected, not run for this research. [CoastsTest](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/test/java/com/pandaismyname1/ultraterraforged/test/CoastsTest.java).

## 4. Noise-router and biome integration

`PresetNoiseRouterData` feeds custom cell fields into continentalness, erosion, weirdness, height offset, temperature, and moisture, then constructs density and cave functions using Minecraft's machinery. The reviewed base-3D-noise node is zero; most above-ground height comes from the cell-based terrain model. A surface-gradient field biases cave entrances toward steep slopes. This confirms that the height-field layer and volumetric cave layer are separate. [PresetNoiseRouterData](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/data/preset/PresetNoiseRouterData.java).

The TerraBlender adapter registers its `uniqueness` density field using UTF's biome-region cell field. That is an explicit bridge between two systems, not evidence that arbitrary biome or terrain generators compose automatically. [TBNoiseRouterData](https://github.com/Pandaismyname1/ultraterraforged/blob/4b3b5c4d42cae3a03053b8c83d8d330b38cf09ba/common/src/main/java/com/pandaismyname1/ultraterraforged/compat/terrablender/TBNoiseRouterData.java).

## 5. Primary research beyond Minecraft mods

### Sparse implicit landforms: the closest research match

Paris, Galin, Peytavie, Guérin, and Gain, **Terrain Amplification with Implicit 3D Features**, ACM TOG 38(5), Article 147, 2019, DOI [10.1145/3342765](https://doi.org/10.1145/3342765). Reviewed the author-hosted full paper, especially §§3, 5, 6.1, and 7. [Author project](https://aparis69.github.io/public_html/projects/paris2019_3D.html), [author-hosted paper](https://people.cs.uct.ac.za/~jgain/wp-content/papercite-data/pdf/paris2019.pdf).

The method embeds a height field into an implicit representation, then adds sparse, bounded feature subtrees. Separate geology and environmental-stress fields guide placement. It demonstrates sea arches and overhanging cliffs, uses compact-support primitives and a bounding-volume hierarchy, and perturbs primitive influence instead of adding arbitrary noise to the entire result. The paper discusses disconnected components caused by unrestricted noise. Its procedural erosion approximates processes; it is not a full physical simulation. **Relevance:** localized 3D amplification is a strong fit for a shore overhaul. **Limit:** mesh extraction, meter-based scenes, finite authoring domains, and reported timings do not establish Minecraft chunk-generation performance. No paper implementation has been ported here.

### Density fields instead of height alone

Ryan Geiss, **Generating Complex Procedural Terrains Using the GPU**, GPU Gems 3, Chapter 1. [NVIDIA publication](https://developer.nvidia.com/gpugems/gpugems3/part-i-geometry/chapter-1-generating-complex-procedural-terrains-using-gpu).

The chapter explains why a single-valued height field cannot represent caves or overhangs and instead samples a signed 3D density field whose boundary defines terrain. Large and small noise scales shape the field before surface extraction. **Relevance:** the desired arch/ledge silhouette requires a volumetric representation somewhere in the pipeline. **Limit:** its GPU rendering architecture and historical hardware results do not apply to Minecraft's Java server generator. This is conceptual guidance, not a recommendation to add GPU world generation.

### Structured drainage before fine detail

Génevaux et al., **Terrain Generation Using Procedural Models Based on Hydrology**, ACM TOG 32(4), Article 143, 2013, DOI [10.1145/2461912.2461996](https://doi.org/10.1145/2461912.2461996). [Author-hosted paper](https://www.cs.purdue.edu/cgvlab/www/resources/papers/Genevaux-ACM_Trans_Graph-2013-Terrain_Generation_Using_Procedural_Models_Based_on_Hydrology.pdf); abstract and model overview reviewed.

The approach builds drainage/watershed structure and combines procedural terrain and river patches through a hierarchy of operations. **Relevance:** coherent large-scale context should organize fine detail. A coast can likewise provide the shared context for a headland, beach, and adjacent shelf. **Limit:** a drainage construction method does not itself solve arch geometry; its finite/global graph is not a drop-in infinite-world algorithm.

### Tectonic uplift and stream-power erosion

Cordonnier et al., **Large Scale Terrain Generation from Tectonic Uplift and Fluvial Erosion**, Computer Graphics Forum 35(2), 165–175, 2016, DOI [10.1111/cgf.12820](https://doi.org/10.1111/cgf.12820). [Author publication and abstract](https://perso.liris.cnrs.fr/apeytavi/website/publication/hal-01262376/); abstract reviewed, not a full reproduction of the solver.

The method combines uplift controls, stream-network evolution, erosion, and terrain reconstruction. **Relevance:** a landform can be conditioned on a meaningful environmental field instead of appearing uniformly everywhere. **Limit:** dynamic erosion has state, neighborhood, and cost implications. Calling a noise channel `erosion` does not implement this physical model.

### Feature spacing and infinite-world determinism

Robert Bridson, **Fast Poisson Disk Sampling in Arbitrary Dimensions**, SIGGRAPH 2007 Sketches. [Author-hosted complete paper](https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph07-poissondisk.pdf).

The algorithm maintains a spatial grid and active list and samples candidates in an annulus around accepted points, enforcing a minimum separation in linear time for a fixed attempt bound over a finite domain. **Relevance:** minimum spacing helps avoid landmark clutter. **Limit:** independently running it per chunk creates seams and order dependence. Our proposed alternative is deterministic world-coordinate candidates plus a bounded neighbor conflict rule; that is an adaptation of the spacing goal, not a claim that our sampler is Bridson's algorithm or statistically equivalent.

### Practical noise-library options

[FastNoiseLite's Java implementation](https://github.com/Auburn/FastNoiseLite/blob/785f37a9ad76e283586a379675085f2063ae03f7/Java/FastNoiseLite.java), pinned at `785f37a9ad76e283586a379675085f2063ae03f7`, provides 2D/3D sampling, several coherent-noise families, fractal modes, and domain warping. It uses mutable configuration and coordinate objects for warp calls. The source includes MIT notices. **Proposal:** compare a small, fixed configuration against our current noise implementation if profiling identifies noise evaluation as material. Do not mutate shared configuration during generation; test coordinate precision at large positive/negative positions. A library's C++ benchmark does not establish speed in our Java workload. Changing a noise implementation also changes deterministic terrain unless explicitly versioned.

[FastNoise2](https://github.com/Auburn/FastNoise2) is an additional C++ SIMD/node-graph lead, reviewed at the documentation level only. It is not established as a Java 17 integration option for our pack. Native-library complexity and deployment costs require a separate study before adoption.

## 6. Geology as a visual design constraint

The National Park Service's [Rocky Coast Landforms](https://www.nps.gov/articles/rocky-coast-landforms.htm) explains sheltered sandy pocket beaches between exposed headlands, coastal arches/stacks, and the influence of bedrock structure on cliff profiles. Its [Sea/Littoral Caves](https://www.nps.gov/subjects/caves/sea-or-littoral-caves.htm) describes wave erosion along weaknesses, eventual openings through rock, and roof collapse into stacks.

**Design inference:** favor sand in suitable embayments, leave exposed cliff/headland segments intact, and place an arch's opening within a projecting body of rock. These sources support geological plausibility, not exact game dimensions or a requirement that every overhang have a cave. The user's custom references remain the visual target; our elevated pools are an intentional game design choice and need not be restricted to a literal intertidal zone.

## Questions this review does not resolve

- Which V9 defects remain visible in fresh in-game terrain? Await the user's V9 observations.
- Whether per-chunk structures, density composition, or a hybrid are faster for our landmark size and frequency. Requires like-for-like profiling.
- Whether a smoother root blend improves our silhouettes without burying openings or erasing cliff character. Requires controlled comparisons.
- Whether our current ocean-reach bound is enough for the reference scale across narrow coasts. Requires complete-footprint measurements, not just a center-point biome test.
- Compatibility with UltraTerraForged as an installed generator. Source study alone does not validate that configuration.

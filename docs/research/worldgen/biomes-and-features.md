# Biome regions, surfaces, and procedural features

[Research index](README.md)

Research date: **2026-10-09**. Scope: TerraBlender, Biomes O' Plenty, Regions Unexplored, Geophilic, and Oh The Biomes We've Gone (BWG), with lessons for Stony Revival's coastal terrain.

This chapter distinguishes three kinds of evidence:

- **Source verified:** behavior directly visible in the pinned implementation or generated resource named beside the claim.
- **Author documented:** the project's own description or dependency listing; dates and supported versions still matter.
- **Proposed experiment:** our interpretation or an original design idea. It has not been implemented, benchmarked, or validated in a modpack by this research.

The main lesson is to separate **where a biome is selected**, **what materials cover it**, and **where a rock formation may extend**. None of these needs to share the same footprint. A region weight is a poor substitute for allocating physical space between a cliff, beach, and ocean.

## Reviewed versions and limits

| Project | Source snapshot reviewed | Relevant version boundary |
|---|---|---|
| TerraBlender | `1.20.1`, [76361917][tb-commit] | Minecraft 1.20.1 / Forge 47.1.0 source branch. Includes a surface-rule caching change committed October 4, 2026; an installed older 3.0.x jar may lack it. |
| Biomes O' Plenty | `1.20.1`, [68b81eeb][bop-commit] | Minecraft 1.20.1; build metadata requires TerraBlender 3.0.1.7 and Forge 47.3.0 or later. Branch head is a source snapshot, not proof of the installed release. |
| Regions Unexplored | historical Forge `1.20.1_FINAL`, [4740f326][ru-commit] | Build metadata says 0.5.6, Minecraft 1.20.1, TerraBlender 3.0.0.163. The newer multiloader repository describes a different generation/dependency era. |
| BWG | `1.20.1`, [4a4459e0][bwg-commit] | Build metadata says **1.8.2-SNAPSHOT**, Forge 47.4.26, TerraBlender 3.0.1.10, CorgiLib 4.0.3.5. Findings are not a claim about every stable BWG artifact. |
| Geophilic | `latest`, [35578706][geo-commit] | Pack 3.7 has base format 15 and later-version overlays. Reviewed base resources and the separately generated `biomes_1_20` definition; newer overlays must not be treated as Minecraft 1.20.1 behavior. |

Version evidence: [TerraBlender build metadata][tb-version], [BOP build metadata][bop-version], [RU build metadata][ru-version], [BWG build metadata][bwg-version], [Geophilic pack metadata][geo-pack]. This was a static implementation review, not an execution test of these five projects.

## TerraBlender: spatial regions select climate mappings

**Source verified.** A `Region` provides a namespace, dimension type, weight, and climate-to-biome mappings. Its API accepts temperature, humidity, continentalness, erosion, depth, weirdness, and offset. `addBiomeSimilar` obtains the vanilla parameter points for a biome; modified-vanilla builders provide a controlled way to adjust those mappings. This operates on biome selection, rather than constructing cliffs or extending shelves. [Region API][tb-region], [vanilla mapping utility][tb-utils]

**Source verified.** The region map begins with weighted selection among regions that supply biomes. Fuzzy and normal zoom layers then expand that map, including configurable zoom counts. Consequently, weight controls how frequently a region family is selected; region-size configuration controls the spatial scale of that family. Neither is an exact stony-shore width or a guaranteed beach-to-cliff distance. The reviewed Overworld default size is 3 zooms. [InitialLayer][tb-initial], [LayeredNoiseUtil][tb-layer], [configuration][tb-config]

**Source verified.** `MixinParameterList` builds a climate search tree for each region. Region zero preserves the parameter values supplied by the existing biome source, including datapack values. Lookup selects a region using position, searches its climate tree, and falls back to region zero for a deferred placeholder. This explains a coexistence mechanism for biome providers; it does not resolve competing density functions, resource overrides, or feature write conflicts. [climate lookup mixin][tb-lookup]

**Source verified.** Surface rules are separately registered by namespace and dimension, with a fallback base rule and optional priority stages around bedrock rules. The reviewed namespace rule implementation retains the selected rule while the biome holder remains unchanged. This is a narrow caching opportunity for repeated surface material queries; it says nothing about the cost of constructing an arch. [surface API][tb-surface-api], [namespace dispatch and cache][tb-surface-cache]

**Proposed experiment for Stony Revival.** Retain the selected biome for ecological compatibility while deriving a separate, smooth coastal support field. Let a stony cliff anchor authorize a bounded formation footprint into an adjacent ocean. Investigate climate-map changes only if measured eligible coast remains scarce after this geometric separation. Raising a region weight could increase the presence of an entire biome family without giving each cliff more usable space.

## Biomes O' Plenty: climate suitability and material rules remain separate

**Source verified.** BOP registers primary, secondary, and rare region families with default Overworld weights 10, 8, and 2. Its primary mapping builder follows climate slices, using a coast continentalness interval of `[-0.19, -0.11]` and separate erosion choices. Several coastal slices choose stony shores under lower erosion and beaches under other erosion ranges. The stony-shore selector usually falls back to vanilla `stony_shore`, but substitutes Rocky Rainforest or Lush Desert in some warm/humid cells. Surface mappings are added at two depth points, while underground mappings use their own depth range. [generation configuration][bop-config], [Overworld climate builder][bop-biomes]

**Source verified.** Surface rules assign materials using biome, elevation, depth, water, and noise conditions. Dune Beach receives sand over sandstone; Origin Valley uses different materials near its lower shoreline and above it. A low-frequency palette decision can therefore create coherent material patches without requiring a matching rectangular height terrace. These rules color an existing surface; they are not themselves a full landform model. [surface rule data][bop-surfaces]

**Source verified.** Placed features compose candidate count or rarity, horizontal spread, heightmap selection, block/fluid predicates, and a biome filter. BOP's water-material disks require water and use an appropriate heightmap; lakes use different candidate frequencies. Despite its name, `GravelCliffFeature` scans the chunk's gravel surface and removes exposed gravel under conditions, rather than creating a new cliff mass. Names and screenshots alone can misidentify which subsystem supplies the terrain. [miscellaneous placements][bop-placement], [GravelCliffFeature][bop-gravel]

**Proposed experiment.** Give the beach a continuous sand-dominant material mask with rare stone windows, independently of the coastal height profile. Sample candidate support before expensive feature construction. Record material owner/stage when diagnosing grass patches, so late decoration is distinguishable from our terrain and surface rules. Do not assume all biome families expose vanilla stony-shore IDs.

## Regions Unexplored: custom shores and bounded rock construction

**Source verified for the historical 1.20.1 snapshot.** RU registers primary and secondary Overworld regions and a Nether region through TerraBlender, and separately registers namespace surface rules. Default stone-shore choices include vanilla Stony Shore for colder selections and Chalk Cliffs for warmer selections; beach choices include Gravel Beach and Grassy Beach. A custom-regions switch controls whether configurable replacements are consulted. Its climate builder puts stone shores in specific coastal erosion slices, so eligibility depends on climate mapping as well as the material visible in a screenshot. [registration][ru-register], [default selections][ru-default], [climate builder][ru-biomes]

**Source verified.** Chalk Cliffs has its own chalk/chalk-grass surface sequence. RU surface rules also distinguish water, altitude, steepness, and noise palettes. `CoastalBiomes` supplies biome features and presentation; that class alone does not generate the geometric volume implied by the name Chalk Cliffs. [surface rules][ru-surfaces], [coastal biome definitions][ru-coastal]

**Source verified.** `SeaRockFeature` builds round or elongated rock sections, including submerged foundations, varies section dimensions with height, and restricts replacement to air, snow, ice, and water. Its cleanup removes unsupported blocks and thin exposed projections, restoring water below sea level. Rocky Reef schedules these rocks at `LOCAL_MODIFICATIONS`; candidates use noise-based count, horizontal spread, an ocean-floor heightmap, and a biome filter. [rock geometry and cleanup][ru-searock], [aquatic placements][ru-aquatic-placement], [Rocky Reef feature stage][ru-aquatic-biome]

**Source verified with a caution.** `RockPillarFeature` embeds its starting geometry and tapers successive blobs, but some helpers instantiate an unseeded `java.util.Random` while other decisions use the feature context's random source. This raises a reproducibility concern; no runtime nondeterminism test was performed. General unsupported-block cleanup would also erase intended cantilever geometry if transplanted into our overhangs. [pillar implementation][ru-pillar]

**Author documented version split.** RU's current dependency listing specifies Lithostitched 1.7.9 or later for 0.6+, and TerraBlender for 0.5.9 and below. The 1.20.1 Forge implementation reviewed here belongs to the older family. Newer source and newer compatibility claims cannot establish that old build's behavior. [official dependency listing](https://www.curseforge.com/minecraft/mc-mods/regions-unexplored)

**Proposed experiment.** Define an explicit compatible-rock-coast tag/config rather than treating every coastal biome as vanilla Stony Shore. Test Chalk Cliffs separately because its materials and later features belong to another mod. Borrow the concepts of embedded foundations and tapered cross sections, but implement deterministic seeds and shape-specific validation: pillars need ground support; arches need continuous buttresses and a roof; overhangs deliberately lack support immediately below the lip.

## BWG: a directly relevant additive arch implementation

**Source verified.** BWG's TerraBlender integration supplies multiple configurable region maps with separate ocean, beach, middle, plateau, slope, and peak selections. Disabled biome entries may defer to the underlying region, and validation checks invalid or duplicated replacement arrangements. Its surfaces include weighted palettes and repeating noise bands. Those are biome/material mechanisms, distinct from its arch structures. [region implementation][bwg-region], [surface palettes][bwg-surfaces]

**Source verified.** `ArchStructure` constructs two terrain-height anchors, embedded 10 blocks below sampled ground, and a curved connection through a raised center. It sweeps noisy spherical volumes along that connection. The opening results from adding the curved mass rather than digging a shaft through an existing plateau. Geometry is grouped by affected chunk into serialized pieces. Orientation comes from random horizontal offsets; no ocean-facing normal or coastal-cliff attachment check appears in this class. This is an excellent architectural analogue, not an ocean-arch solution ready to adopt. [arch planning and geometry][bwg-arch]

**Source verified.** `ArchPiece` stores the origin, endpoints, height, generator configuration, and checked block placements. During generation it reconstructs bounded geometry, deduplicates packed block positions, acquires the target chunk once, and applies material predicates there. This illustrates a repeatable plan distributed across chunks rather than a feature making unrestricted writes into distant chunks. [arch piece serialization and placement][bwg-piece], [parameter codec][bwg-arch-config]

**Source verified example, not a recommended coastal setting.** The generated Dripstone Arch resource uses length 64–200, height 50–150, thickness 10–30, noise frequency 0.09–0.2, and step distances of 8. It runs at `raw_generation`, admits air/water for stone placement, and adds another predicate-driven material pass. Its biome tag selects BWG Dead Sea. Random-spread placement uses spacing 8 and separation 2 chunks, with triangular spread. These are candidate settings, not a guarantee of one successful arch every eight chunks. [arch resource][bwg-arch-json], [biome tag][bwg-arch-tag], [structure-set resource][bwg-arch-set]

**Proposed experiment, highest relevance.** Compare a purpose-built coastal rock buttress/arch against the current density-only approach: choose a cliff anchor and outward ocean direction first, blend a substantial rock body into the cliff, then form a transverse opening through that body. A swept field or implicit solid can supply natural asymmetry; perturbing every voxel independently would produce surface noise. Constrain roof thickness, footprint, connection width, and foundation depth before generating it.

**Performance limit.** Source organization alone is not evidence that this implementation is faster. The planner evaluates formation volume, pieces regenerate bounded geometry, and the sphere-stamping helper creates a noise object per invocation. Benchmark our own plan construction, overlapping stamp work, allocations, deduplication, and chunk application before choosing this architecture. [arch helper][bwg-arch], [piece generation][bwg-piece]

## Geophilic: decoration can materially reshape a stony shore

**Source verified.** Geophilic's stony-shore definition adds large/small pillars and puddles in the surface-structures feature list, then moss and related decoration later. No noise router, density-function, or biome-climate-map definition was found in the reviewed base resource tree. The biome JSON is an actual replacement definition, not an automatically merging instruction. The repository also contains generated definitions for different Minecraft versions. [base stony-shore definition][geo-biome], [generated 1.20 definition][geo-biome-120], [base resource tree][geo-tree]

**Source verified.** Large pillar placement combines noise-based count, horizontal spread, zero allowed surface-water depth, ocean-floor heightmap, and biome filter. Its configured feature uses nested vanilla random/vegetation patches to place stone; this is a data-driven way to repurpose patch machinery for rock formations. Small pillars use a shorter, narrower variant. Puddles place a vanilla `delta_feature` with water contents and stone rim, using a count of eight candidates and a surface heightmap. These simple features can coexist visually with a terrain mod while still competing with its custom coastal details. [pillar placement][geo-pillar-placement], [large pillar configuration][geo-pillar-large], [small pillar configuration][geo-pillar-small], [puddle placement][geo-puddle-placement], [puddle configuration][geo-puddle]

**Source verified.** The development script starts from vanilla biome definitions and replaces selected feature stages/effects/spawn fields with version-specific overlays. This protects version-specific vanilla additions during authoring; it is not runtime arbitration between arbitrary mods' biome JSONs. The reviewed script's overlay path currently targets a newer Minecraft version. [authoring README][geo-readme], [biome-generation script][geo-script]

**Author documented.** The author describes compatibility with terrain-only packs such as Tectonic, and directs Terralith users to Terraphilic. That description was last updated October 24, 2024. It supports the resource-layer distinction, but does not prove all current versions, arbitrary load orders, or Stony Revival's feature interactions are compatible. [official compatibility notes](https://www.curseforge.com/minecraft/mc-mods/geophilic)

**Proposed experiment.** Test Stony Revival with Geophilic both enabled and disabled at the same seed/locations, and record resulting blocks after each generation stage. This is especially relevant to puddle rims, mossy/vegetated beach patches, and unexpectedly modified rock shelves. Do not attribute an observed grass shelf to Geophilic without this evidence. Small vanilla configured features are a useful prototype for modest tide pools; elevated pools still need our own enclosed basin, shallow depth, and support checks.

## Interoperability boundaries for Minecraft 1.20.1 Forge

| Combination or layer | What this review establishes | What remains unproven |
|---|---|---|
| BOP / older RU / BWG with TerraBlender | Their reviewed 1.20.1 implementations register region climate maps and namespace surfaces. | Exact installed dependency compatibility, preserved coastline character, and feature ordering across the full pack. |
| TerraBlender with datapack biome maps, including a Terralith-style provider | The existing parameter values survive as region zero; other region families may be selected elsewhere. | Universal biome/resource integration or unchanged proportions under every region configuration. |
| Geophilic with terrain-only Tectonic | Author documents this pairing; reviewed resource responsibilities explain why it is plausible. | Combined custom coastal features, newer releases, and other pack overrides. |
| Geophilic with Terralith | Author recommends a compatibility pack to reconcile biome definitions. | That a pack for another Minecraft version merges the right 1.20.1 features. |
| RU 0.6+ with Lithostitched | Current official dependency listing identifies the newer dependency family. | Application of that implementation to the historical Forge 1.20.1 branch. |
| Stony Revival geometry extending into an ocean biome | An anchor's biome and a structure's physical bounds can be modeled separately; BWG demonstrates chunk-distributed procedural bounds. | Suitable insertion stage, safe surface/aquifer/decoration interaction, and actual seam-free generation in our pack. |

The matrix deliberately separates **loading together**, **resource composition**, **terrain continuity**, and **visual interaction**. All four require evidence. Region integration does not guarantee that a beach remains sandy after vegetation runs, or that a later puddle feature preserves an elevated basin.

## Original design questions to carry into the next audit

1. **Who owns each coastal zone?** Keep cliff core and plateau support, beach materials, shallow-water profile, and feature footprints as separate continuous fields. Reserve cliff space before allocating beach width.
2. **What makes an anchor valid?** Measure cliff height, solid support, landward connection, outward water direction, and available ocean room once per candidate. Reject unsuitable candidates rather than excavating deep access shafts.
3. **What should blend?** Blend formation geometry into the cliff over a substantial overlap; vary broad silhouette and smaller rock texture at separate scales. Taper a shelf lip gradually along the coast so it does not end as a rectangle.
4. **What should remain shallow?** Tide pools should use a closed bowl with an explicit water level and maximum depth, not a generic deep cavity. Eligibility should consider shelf support and enclosure at each candidate height.
5. **What should be cached?** Cache invariant candidate plans and sampled support within generation scope; derive per-chunk work from bounds. Keep seed derivation independent of chunk completion order, and measure allocation/lookup costs instead of assuming fewer noise calls mean a faster generator.
6. **Who changes the result afterward?** Capture before/after snapshots for terrain, surface rules, structures, local modifications, and vegetation. Pair tests with Geophilic and custom RU/BOP shores can distinguish our defects from interactions.

These are proposed experiments, not implementation commitments. They prioritize natural integration and testable feature ownership over increasing global biome weights.

## Licensing and reuse

The notes are original analysis and contain no copied implementation. TerraBlender's reviewed source identifies LGPL-3.0-or-later; BOP and BWG identify All Rights Reserved. Geophilic's mod metadata and official listing identify All Rights Reserved, while its README explicitly grants MIT licensing to **Python code**, not the entire datapack. The historical RU snapshot lacks a root license file in the reviewed tree, while the current CurseForge page lists MIT. Do not retroactively infer permission for that historical code from a current listing. [TerraBlender license][tb-license], [BOP license][bop-license], [BWG license][bwg-license], [Geophilic metadata][geo-mod], [Geophilic README][geo-readme], [current RU license listing](https://www.curseforge.com/minecraft/mc-mods/regions-unexplored/license)

Use published APIs when appropriate and implement our own algorithms from the concepts. Copying source/resources/assets or distributing a derivative requires reviewing the exact version's applicable terms; public source availability alone is not a reuse license.

## Source index

All GitHub links below are commit-pinned. The five snapshot roots and version files above identify the audit boundary; the links in each section identify the actual classes/resources reviewed.

[tb-commit]: https://github.com/Glitchfiend/TerraBlender/tree/76361917d2d649bfe0457c220c7c8ad2c6c44a6c
[tb-version]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/gradle.properties
[tb-region]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/api/Region.java
[tb-utils]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/worldgen/RegionUtils.java
[tb-initial]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/worldgen/noise/InitialLayer.java
[tb-layer]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/worldgen/noise/LayeredNoiseUtil.java
[tb-config]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/config/TerraBlenderConfig.java
[tb-lookup]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/mixin/MixinParameterList.java
[tb-surface-api]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/api/SurfaceRuleManager.java
[tb-surface-cache]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/Common/src/main/java/terrablender/worldgen/surface/NamespacedSurfaceRuleSource.java
[tb-license]: https://github.com/Glitchfiend/TerraBlender/blob/76361917d2d649bfe0457c220c7c8ad2c6c44a6c/LICENSE
[bop-commit]: https://github.com/Glitchfiend/BiomesOPlenty/tree/68b81eeb3f5218f94efba45f30a060fba3037a32
[bop-version]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/gradle.properties
[bop-config]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/common/src/main/java/biomesoplenty/config/GenerationConfig.java
[bop-biomes]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/common/src/main/java/biomesoplenty/biome/BOPOverworldBiomeBuilder.java
[bop-surfaces]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/common/src/main/java/biomesoplenty/worldgen/BOPSurfaceRuleData.java
[bop-placement]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/common/src/main/java/biomesoplenty/worldgen/placement/BOPMiscOverworldPlacements.java
[bop-gravel]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/common/src/main/java/biomesoplenty/worldgen/feature/misc/GravelCliffFeature.java
[bop-license]: https://github.com/Glitchfiend/BiomesOPlenty/blob/68b81eeb3f5218f94efba45f30a060fba3037a32/LICENSE
[ru-commit]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/tree/4740f3263d746190dcb077318865fc3aa1a40d25
[ru-version]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/gradle.properties
[ru-register]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/registry/BiomeRegistry.java
[ru-default]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/world/level/biome/DefaultBiomes.java
[ru-biomes]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/world/level/biome/RegionPrimaryBiomeBuilder.java
[ru-surfaces]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/data/worldgen/biome/surface/RuSurfaceRuleData.java
[ru-coastal]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/data/worldgen/biome/builder/CoastalBiomes.java
[ru-searock]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/world/level/feature/SeaRockFeature.java
[ru-aquatic-placement]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/data/worldgen/placement/RuAquaticPlacements.java
[ru-aquatic-biome]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/data/worldgen/biome/builder/AquaticBiomes.java
[ru-pillar]: https://github.com/UHQ-GAMES-MODS/REGIONS_UNEXPLORED_FORGE/blob/4740f3263d746190dcb077318865fc3aa1a40d25/src/main/java/net/regions_unexplored/world/level/feature/RockPillarFeature.java
[bwg-commit]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/tree/4a4459e04fad283ca550343b99bc9cd7408323da
[bwg-version]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/gradle.properties
[bwg-region]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/java/net/potionstudios/biomeswevegone/world/level/levelgen/biome/BWGTerraBlenderRegion.java
[bwg-surfaces]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/java/net/potionstudios/biomeswevegone/world/level/levelgen/biome/BWGOverworldSurfaceRules.java
[bwg-arch]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/java/net/potionstudios/biomeswevegone/world/level/levelgen/structure/arch/ArchStructure.java
[bwg-piece]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/java/net/potionstudios/biomeswevegone/world/level/levelgen/structure/arch/ArchPiece.java
[bwg-arch-config]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/java/net/potionstudios/biomeswevegone/world/level/levelgen/structure/arch/ArchConfig.java
[bwg-arch-json]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/generated/resources/data/biomeswevegone/worldgen/structure/dripstone_arch.json
[bwg-arch-tag]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/generated/resources/data/biomeswevegone/tags/worldgen/biome/dripstone_arch.json
[bwg-arch-set]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/Common/src/main/generated/resources/data/biomeswevegone/worldgen/structure_set/dripstone_arches.json
[bwg-license]: https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone/blob/4a4459e04fad283ca550343b99bc9cd7408323da/LICENSE
[geo-commit]: https://github.com/everloste/Geophilic/tree/355787069ec37309007f72fb394e8f07ece6d05c
[geo-pack]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/pack.mcmeta
[geo-biome]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/minecraft/worldgen/biome/stony_shore.json
[geo-biome-120]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/biomes_1_20/data/minecraft/worldgen/biome/stony_shore.json
[geo-tree]: https://github.com/everloste/Geophilic/tree/355787069ec37309007f72fb394e8f07ece6d05c/data
[geo-pillar-placement]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/geophilic/worldgen/placed_feature/stony_shore/pillars/large.json
[geo-pillar-large]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/geophilic/worldgen/configured_feature/biome_specific/stony_shore/pillar/large.json
[geo-pillar-small]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/geophilic/worldgen/configured_feature/biome_specific/stony_shore/pillar/small.json
[geo-puddle-placement]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/geophilic/worldgen/placed_feature/stony_shore/puddles.json
[geo-puddle]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/data/geophilic/worldgen/configured_feature/biome_specific/stony_shore/puddle.json
[geo-readme]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/README.md
[geo-script]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/.dev/biome%20blender/biomeblender.py
[geo-mod]: https://github.com/everloste/Geophilic/blob/355787069ec37309007f72fb394e8f07ece6d05c/fabric.mod.json

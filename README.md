# Stony Shore Revival — V2 test build 0.2.0

A Forge 1.20.1 source project for an additive stony-shore overhaul. The GitHub Actions workflow builds an installable remapped JAR and stores it as a workflow artifact. Do not put the source ZIP into `mods`.

## What this prototype does

- Uses Forge's `add_features` biome modifier on `minecraft:stony_shore`. It does not replace the biome, noise settings, structure sets, surface rules, or neighboring terrain generation.
- Retextures naturally exposed stone, calcite, granite, and andesite in coherent 42-block bands. Keeps most existing calcite and granite. Does not replace ores, plants, structures, or arbitrary modded blocks.
- Examines open cliff faces down to 92 blocks below the local top. The original cliff geometry stays in place.
- Attempts tide pools in half of shore chunks at up to eight sites. Each has a radius-two shallow basin, a radius-three natural rock rim and solid floor, and may receive sea pickles or seagrass. Rock shelves up to 24 blocks above sea level qualify.
- Attempts tapered stone spires in half of shore chunks at up to six sites, including high shelves up to 96 blocks above sea level. They do not use dripstone.
- Adds coherent sand and gravel cove patches on the upper surface of low ocean-facing shore, sampling ocean biomes 4, 8, and 12 blocks away.
- Adds broad wet patches of moss blocks and mossy cobblestone. If Biomes We've Gone is present, its `overgrown_stone`, `mossy_stone`, and `rocky_stone` are selected from its registry. A confirmed `verdant_stone` block from several optional namespaces takes precedence over its mossy stone; absent IDs are ignored.
- Samples the shore and nearby biomes for cold temperatures, with scattered snow and packed ice accents.
- Accepts optional full-cube block IDs in `config/stonyshorerevival-common.toml`; missing registry entries are skipped. No guessed mod IDs are hardcoded.

## Build and use

The `Build Forge mod` GitHub Actions workflow installs Java 17 and Java 25, runs Gradle 9.3.1 on Java 25, and compiles Minecraft 1.20.1 bytecode with the Java 17 toolchain. Its downloadable artifact contains the `-srg.jar` for use in the pack. For a local build, install both JDKs and Gradle 9.3.1, run `gradle clean build`, and use only the `-srg.jar` under `build/libs/`.

Use a duplicate ATM9 instance and a backed-up world for the first test; put the built JAR in that instance's `mods` folder on both sides if running a server. The new feature appears **only in newly generated chunks**. In a new creative test world, use `/locate biome minecraft:stony_shore` and compare warm and cold shores from several angles. Turn off shaders for one comparison if block colors are hard to judge.

The Forge version in `gradle.properties` is a development baseline (`47.2.0`); match your installed Forge 47.x when testing. A visual match with the screenshot is not proof of compatibility with a customized instance. Supply the exact mod list, Forge version, and worldgen datapacks before a release build.

## Config

After first load, edit `config/stonyshorerevival-common.toml` while the game is closed:

```toml
[generation]
stone_replacement_chance = 0.42
tide_pools = true
stone_spires = true
cold_shores = true
extra_rocks = []
```

The V2 config also has `pool_chunk_chance = 0.50` and `spire_chunk_chance = 0.50` (both range 0–1). These are attempt rates, not guaranteed placements; pools still need a relatively flat, intact rock shelf. Only use confirmed full-cube stone-like block IDs for `extra_rocks`. Changes affect future chunks. Biomes We've Gone's overgrown stone can naturally spread onto adjacent stone because the block has its own random tick behavior.

## Current scope and next iteration

This version adds surface detail and small features. It does not yet change continent shape, make root-bearing coastal trees, introduce new stone stalagmite blocks, or place Hybrid Aquatic plants. Those need the exact installed mod JARs / registry IDs and, for landform work, the current terrain and biome datapack stack. True landform edits require careful work with the pack's existing density functions or terrain generator and should be tested on a copied world.

The supplied V1 `latest.log` crash stack points at `terrain_slabs-forge-4.1.1-beta.jar` trying to assign a dripstone thickness property to `minecraft:water` during chunk loading. Stony Shore Revival has no stack frame there and V1 did not create pointed dripstone. An indirect interaction is not proven or completely excluded; reproduce in a copy of the world with Terrain Slabs' spike conversion disabled or that mod temporarily removed, and retain the full crash report if it recurs.

## What to watch for during testing

Check chunk boundaries, pools draining into caves, spires intersecting existing vegetation, and the border between cold and warm biomes. If a feature looks too common, disable its config flag and generate fresh chunks. Keep your main save backed up: removing a worldgen mod after visiting generated areas can leave its placed blocks behind, especially optional modded blocks.

# Stony Shore Revival — ATM9 prototype 0.1.0

A Forge 1.20.1 source project for an additive stony-shore overhaul. The GitHub Actions workflow builds an installable remapped JAR and stores it as a workflow artifact. Do not put the source ZIP into `mods`.

## What this prototype does

- Uses Forge's `add_features` biome modifier on `minecraft:stony_shore`. It does not replace the biome, noise settings, structure sets, surface rules, or neighboring terrain generation.
- Retextures naturally exposed stone, calcite, granite, and andesite in coherent 42-block bands. Keeps most existing calcite and granite. Does not replace ores, plants, structures, or arbitrary modded blocks.
- Examines open cliff faces down to 92 blocks below the local top. The original cliff geometry stays in place.
- Adds occasional small tide pools only when the entire 9×9 candidate is rock, inside the biome, flat, above sea level, and has a solid base. Pools can receive sea pickles or seagrass.
- Adds occasional short, irregular rock spires where there is clear headroom.
- Allows sparse sand and gravel at low ocean-facing shore edges; samples nearby ocean biome tags.
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

Only use valid full-cube stone-like block IDs for `extra_rocks`, for example `modid:known_block` after confirming the ID with F3 or the mod JAR registry. Changing the config affects future chunks, not existing terrain. The generic optional palette is intentionally restrained until the actual blocks and texture packs can be checked together.

## Current scope and next iteration

This version adds surface detail and small features. It does not yet change continent shape, make root-bearing coastal trees, introduce new stone stalagmite blocks, or place Hybrid Aquatic plants. Those need your exact installed mod JARs / registry IDs and, for the landform work, the current terrain and biome datapack stack. After the first in-game screenshots, tune palette weights and feature frequency, then add those larger features with separate toggles. True landform edits require careful work with the pack's existing density functions or terrain generator and should be tested on a copied world.

## What to watch for during testing

Check chunk boundaries, pools draining into caves, spires intersecting existing vegetation, and the border between cold and warm biomes. If a feature looks too common, disable its config flag and generate fresh chunks. Keep your main save backed up: removing a worldgen mod after visiting generated areas can leave its placed blocks behind, especially optional modded blocks.

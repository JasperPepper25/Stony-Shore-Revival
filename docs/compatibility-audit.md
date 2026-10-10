> Historical V3 evidence. Superseded by [the October 2 post-WWOO audit](current-pack-audit.md).

# Provisional pack evidence

The supplied October 1 log identifies Stony Shore Revival **0.3.0**, so this is
historical evidence rather than proof of the current V4 test environment.
Only technical observations are recorded here; the original log is not committed.

| Component | Observed version |
| --- | --- |
| Minecraft / Forge | 1.20.1 / 47.4.16 |
| Tectonic | 3.0.17 |
| Terralith | 2.5.4 |
| Terralith Restoned | 1.3 |
| Lithostitched | 1.4.11 |
| TerraBlender | 3.0.1.10 |
| Biolith | 1.0.1-beta.1 |
| Biome Replacer | 3.1-hippo |
| Biomes O' Plenty | 19.0.0.96 |
| Oh The Biomes We've Gone | 1.8.0 |
| Regions Unexplored | 0.5.6 |
| Fragmentum | 5.0.0 |
| Terrain Slabs | 4.1.1-beta |
| FastChunkGen | 0.3 |

The log mentions Tectonic's Terratonic overlay, a generated Fragmentum layer,
Lithostitched's seed-parity pack, and an edited Terralith JAR filename. These
messages do not establish final selected pack order or which resource wins.
Multiple discovery filenames are not proof of duplicate active mods.
Biome Replacer reports TerraBlender/Biolith integration, three overworld replacement
rules and seven removal rules. Stock biome distribution is therefore not a safe
assumption for integration planning.

Missing: the current world's resolved generator/noise graph, winning density and
surface resources, selected pack order, and the actual replacement/config rules.
The audit ZIP is designed to gather these without requesting the entire instance.
Code-driven mixins may still need source inspection after the resource audit.
Compatibility is not guaranteed by retaining Forge or by using a biome mask alone.

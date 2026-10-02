# Coastal terrain rewrite: first milestone

Branch: `terrain-rewrite`. Preserved release: `baseline/v0.4.0` at `a705ce3`.
Build: **0.5.0-audit.1**, a diagnostic foundation, not the new tide-pool release.

## What runs now

The existing V4 feature is split into surface, legacy pool, and spire passes with
its original order, configuration keys and registry IDs retained. `/stonyshore audit`
requires operator permission (level 2) and writes a local ZIP beneath
`stonyshore-audits` in the game instance. It does not generate or edit chunks.
Run it in the actual test world with the current pack, then attach the ZIP.
Cheats must be available in singleplayer; a server operator can run it from console.

The export contains loaded mod versions, selected pack IDs, dimension generator
and biome-source classes/codecs, resolved noise settings, density functions, stony-shore biome and feature definitions,
terrain/biome JSON resource stacks and effective source-pack names, plus allowlisted
worldgen JSON/TOML configs. `completion.json` reports omitted files and read errors;
individual resolved encodings can also contain `encodingError` fields. Caps are
2 MiB per entry and 64 MiB of payload. Exporting is synchronous and may briefly
pause the server; run once while standing still. The ZIP is never uploaded automatically.
It does not deliberately collect a world seed, logs, player data, or server connection
settings. Pack IDs and copied config values remain visible in the report.

## Prototype and integration boundary

`CoastalTerrainPlanner` is a pure, world-seeded mathematical prototype. It combines
warped fields at several scales into shelf and basin targets in absolute coordinates.
The supplied shore mask fades its influence; the prototype changes eligible surfaces
by at most -8/+3 blocks and fades out on high coasts and deep water. It has **no live
world-generation hook**. A column target alone does not solve aquifers, cave roofs,
structures, surface rules, or fluid containment.

The next implementation decision depends on the exported effective terrain graph.
Prefer a localized density-stage modification that composes with the loaded terrain
and retains its biome source, aquifers, cave functions and surface-rule chain. Do not
replace the global noise-settings JSON or entire chunk generator. There is no assumed
universal per-biome terrain-noise replacement hook; the supported insertion point must
be established for the installed versions. Unsupported generators should keep existing
behavior with a clear diagnostic, rather than receive a guessed patch.

Future geometry must be shared across chunks with deterministic sampling and an
explicit bounded biome/coast influence mask. Pools should emerge from broad shore
shelves with connected depressions, exposed islands and several floor elevations.
Water must use a consistent elevation within a connected basin. Chunk-local writes
must consume the same cross-chunk plan without modifying unloaded neighbor chunks.
Surface geology follows terrain, and spire groups get a separate regional placement
field. Preserve structures and avoid opening cave roofs as part of the integration.

## Acceptance gates

1. Resolve current pack settings and choose an insertion point from evidence.
2. Compare identical seeds with the feature off/on in new test worlds: islands,
   continental coasts, steep coasts, biome boundaries and negative coordinates.
3. Check chunk joins, water containment, cave roofs, structures and generation order.
4. Compare visual scale, connected shapes, depth variation and rarity against the
   user's reference screenshots; mathematical tests cannot establish visual quality.
5. Measure generation cost with the actual pack before expanding feature density.

Automated tests currently cover deterministic traversal across positive/negative chunk
coordinates, seed variation, bounded changes, mask fade, excluded elevations, continuity
and invalid inputs. They do not certify in-game compatibility or water behavior.

# Sea arch catalog — terrain.13-arches.1

This arch-focused candidate starts from the accepted **terrain.12-perf.2**
development baseline. It adds twelve equally weighted sea-arch styles and
continuous variation within each style. Perf.3's cache experiment is not included.

## Catalog

Every style has weight **1**, or **1/12 (about 8.33%)** of the catalog.

| Style ID | Exterior and passage |
| --- | --- |
| `RISING_FIN` | Long coastal blade climbing gradually to a rounded seaward nose; broad oval passage. |
| `SWEPT_FIN` | A narrower, sideways-curving rising fin, with a leaning, uneven oval passage. |
| `LOW_WINDOW_FIN` | A rising sea fin with a low, horizontally stretched window beneath its crest. |
| `ROUNDED_BRIDGE` | Familiar broad-headed rock bridge, with a rounded, asymmetric opening. |
| `BROAD_SPAN` | Longer bridge with a wide opening and lower crown. |
| `TALL_SPIRE` | Shorter, narrow arch with a tall slender spine and vertical opening. |
| `POINTED_CROWN` | A pronounced irregular peak above a slightly pointed oval passage. |
| `LOW_SLOT` | Thick headland with a very low, horizontal elliptical keyhole. |
| `UPRIGHT_KEYHOLE` | Fuller outer rock enclosing a tall, narrow vertical keyhole. |
| `STACK_PIER` | Arch joined to a larger weathered sea stack at its outer pier. |
| `SENTINEL` | Arch ending before a separate pointed stack, with a real water/air gap. |
| `STACK_GROUP` | Arch accompanied by two smaller, offset stacks with unequal heights and different caps. |

Style selection uses a seeded shuffled deck: each 4x3 block of planning regions
contains every style once before the existing arch-attempt and terrain filters.
A region retains its selected style through its placement attempts rather than
rerolling to a more easily placed family. All families share an arbitration
envelope, reducing collision bias against long fins and stack-bearing forms.
This gives equal selection weights, not a promise of exactly equal counts on
one short coastline: cliff suitability, failed foundations, overlap rejection
and exploration coverage still affect what is actually encountered.

Within each family, seeded dimensions, lean, bending, asymmetric openings,
coherent face weathering and crown irregularity vary the result. The catalog is
not twelve fixed block templates. Detached stacks are part of their parent arch
plan and require existing seabed support; attached stacks intersect the outer
arch body. Stack groups have different heights and offsets rather than repeated
columns at uniform spacing.

## Reference review

The four supplied `Custom_*` screenshots guide the rising-fin roofline,
rounded nose, broad coastal attachment and crosswise opening. The Tripitos
and vertical cliff examples guide upright gates and heavier piers. The Lalaria
and numbered sea-arch photographs guide pointed crowns, low windows, uneven
erosion, tilted openings and stack remnants beside the outer pier.

Additional primary references:

- [NPS: Rocky Coast Landforms](https://www.nps.gov/articles/rocky-coast-landforms.htm)
  discusses the variety of rocky headlands, arches, stacks and stepped cliff forms.
- [NPS: Sea/Littoral Caves](https://www.nps.gov/subjects/caves/sea-or-littoral-caves.htm)
  illustrates two-sided arches and stack remnants in a coastal erosion sequence.
- [USGS: Sea cliff photograph](https://www.usgs.gov/media/images/a-photo-a-sea-cliff)
  highlights the role of rock fractures in the resulting coastal forms.

These references inform the procedural shapes. There is no simulation of tides
or erosion over game time.

## Scope and compatibility

The changes are limited to arch selection, arch/stack volumes, the cached
horizontal arch section, and arch-specific audit fields. Existing cliff,
beach, pool, cave and overhang formulas, biome policies, terrain adapters and
the perf.2 cache implementation remain the baseline. Nearby feature placement
can change where a new arch's complete footprint reserves space.

Root attachment and two open approaches remain required. Catalog stacks need
seabed foundations. The entire finite arch/stack footprint participates in
neighbour arbitration and lookup, including across chunk boundaries and where
the local coastal mask fades. A short analytic body retains its solid interior;
the opening is the explicit carved passage.

Horizontal body and portal calculations are stored with the existing bounded
shore-column cache. Repeated density samples reuse that section and create no
new section objects. This limits the cost of the richer geometry, but does not
establish Minecraft performance without the usual in-game capture.

## Installation and testing

Use `stony-shore-revival-0.8.0-terrain.13-arches.1-srg.jar` as the single Stony
Shore Revival JAR in a duplicate ATM9 instance. Replace the perf.2 JAR there;
keep perf.2 available as the reference. Existing generated terrain is unchanged.

1. Fully close Java, install this candidate and create a new test world with
   the same seed/preset and existing settings. Leave `coastal_arches=true` and
   retain the established Tectonic/Terralith/Lithostitched setup.
2. For the controlled comparison, keep the same pre-route wait and west-then-south
   route from X=-467, Z=679 to X=-1029, Z=940. Use the seven-minute v1.2 collector
   and keep other settings fixed. There is no need to change configs for the catalog.
3. Explore additional fresh coastline to review variety. A seven-minute route
   is unlikely to contain every style. Look from both sides and from above;
   the swept fin and offset stack group are especially different in plan view.
4. Use `/stonyshore mark <label>` directly above a formation worth reviewing.
   Include one mark per distinct example, up to the existing sixteen-site limit.
5. After profiling ends, run `/stonyshore audit`. Arch checks now include
   `catalogStyle`, `catalogLabel`, `catalogStyleWeight`, opening center/tilt
   and stack locations, planned floors and heights. Style-specific selected,
   supported and accepted counters appear in the existing landform statistics.

Those counters count planning evaluations and can increase after cache eviction;
they are not unique-world counts or timed performance deltas. The existing
overworld noise-settings serialization warning may still make an audit partial;
arch checks and the successful diagnostic sections remain useful.

When reporting appearance, include the marked audit and screenshots of the
whole silhouette, both portals, root, roof and nearby stacks. Note any roof
breach, isolated floating blocks, blocked portal, clipped footprint or visible
repeat pattern. Candidate acceptance and merging remain subject to that in-game
review; `main` stays on the accepted perf.2 baseline during testing.

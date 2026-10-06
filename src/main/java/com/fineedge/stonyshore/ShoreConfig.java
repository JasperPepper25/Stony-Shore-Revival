package com.fineedge.stonyshore;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class ShoreConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue COASTAL_TERRAIN;
    public static final ForgeConfigSpec.BooleanValue SANDY_SHELVES;
    public static final ForgeConfigSpec.DoubleValue BEACH_FREQUENCY;
    public static final ForgeConfigSpec.BooleanValue LANDFORMS;
    public static final ForgeConfigSpec.BooleanValue ARCHES;
    public static final ForgeConfigSpec.BooleanValue POOLS;
    public static final ForgeConfigSpec.BooleanValue SPIRES;
    public static final ForgeConfigSpec.BooleanValue COLD;
    public static final ForgeConfigSpec.DoubleValue STONE_CHANCE;
    public static final ForgeConfigSpec.DoubleValue POOL_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SPIRE_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_ROCKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Generation happens only in new chunks. These settings belong on the server.").push("generation");
        STONE_CHANCE = b.comment("Chance to texture a naturally exposed stone face; 0 keeps original stone.")
            .defineInRange("stone_replacement_chance", 0.42, 0.0, 1.0);
        COASTAL_TERRAIN = b.comment("Native coastal density composition for the audited Tectonic/Terralith pack. Restart required. Suspends legacy pools and spires when installed.")
            .define("coastal_terrain", true);
        SANDY_SHELVES = b.comment("Occasional broad low sand shelves within coastal terrain shaping. Restart required.")
            .define("coastal_sandy_shelves", true);
        BEACH_FREQUENCY = b.comment("Broad beach field coverage, independent of biome rarity. Restart required.")
            .defineInRange("coastal_beach_frequency",0.65,0.0,1.0);
        LANDFORMS = b.comment("Local upper shore ledges, contained elevated pools, and cliff-foot beaches. Restart required.")
            .define("coastal_landforms", true);
        ARCHES = b.comment("Occasional carved arches in suitable shore headlands. Requires coastal_landforms; restart required.")
            .define("coastal_arches", true);
        POOLS = b.define("tide_pools", true);
        SPIRES = b.define("stone_spires", true);
        POOL_CHANCE = b.comment("Chance to attempt pools in each shore chunk; larger irregular basins are tried first, up to two can succeed.")
            .defineInRange("pool_chunk_chance", 0.80, 0.0, 1.0);
        SPIRE_CHANCE = b.comment("Chance to attempt a local group of up to three varied stone spires in each shore chunk.")
            .defineInRange("spire_chunk_chance", 0.25, 0.0, 1.0);
        COLD = b.define("cold_shores", true);
        EXTRA_ROCKS = b.comment("Optional block IDs, e.g. modid:block. Missing IDs are ignored. Prefer full cube stone-like blocks.")
            .defineListAllowEmpty("extra_rocks", List::of, o -> o instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"));
        b.pop();
        SPEC = b.build();
    }

    private ShoreConfig() {}
}

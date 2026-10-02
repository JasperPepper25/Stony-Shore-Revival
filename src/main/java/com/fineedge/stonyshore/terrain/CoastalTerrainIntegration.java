package com.fineedge.stonyshore.terrain;

import com.fineedge.stonyshore.ShoreConfig;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.*;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Minecraft 1.20.1 adapter, installed before Forge begins preparing the Overworld spawn. */
public final class CoastalTerrainIntegration {
    private record State(String reason, CoastalColumnSampler columns, AtomicBoolean failed) {}
    private static final Map<ServerLevel, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private CoastalTerrainIntegration() {}

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        if (STATES.containsKey(level)) return;
        if (!ShoreConfig.COASTAL_TERRAIN.get()) { skipped(level, "disabled by coastal_terrain config"); return; }
        var generator = level.getChunkSource().getGenerator();
        if (generator.getClass() != NoiseBasedChunkGenerator.class
            || !(generator.getBiomeSource() instanceof MultiNoiseBiomeSource)) {
            skipped(level, "unsupported generator or biome-source class"); return;
        }
        if (!version("tectonic", "3.0.17") || !version("terralith", "2.5.4")
            || !version("lithostitched", "1.4.11") || ModList.get().isLoaded("wwoo_forge")) {
            skipped(level, "prototype requires audited Tectonic 3.0.17 / Terralith 2.5.4 / Lithostitched 1.4.11 without WWOO"); return;
        }
        if (generator.getSeaLevel() != 63) { skipped(level, "unsupported sea level"); return; }
        RandomState random = level.getChunkSource().randomState();
        NoiseRouter original = random.router();
        var sampler = random.sampler();
        var source = generator.getBiomeSource();
        AtomicBoolean failed = new AtomicBoolean();
        CoastalColumnSampler columns = new CoastalColumnSampler(level.getSeed(), 63,
            (x, y, z) -> original.finalDensity().compute(new DensityFunction.SinglePointContext(x, y, z)),
            (x, z) -> source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(65), QuartPos.fromBlock(z), sampler)
                .is(Biomes.STONY_SHORE));
        NoiseRouter replacement = new NoiseRouter(original.barrierNoise(), original.fluidLevelFloodednessNoise(),
            original.fluidLevelSpreadNoise(), original.lavaNoise(), original.temperature(), original.vegetation(),
            original.continents(), original.erosion(), original.depth(), original.ridges(),
            new CoastalDensity(original.initialDensityWithoutJaggedness(), columns, 0.3, failed),
            new CoastalDensity(original.finalDensity(), columns, 0.15, failed),
            original.veinToggle(), original.veinRidged(), original.veinGap());
        try {
            // SRG field for RandomState.router in 1.20.1. Keep this version-specific access isolated.
            // No global registry edit: only this level's already-seeded runtime router is adapted.
            ObfuscationReflectionHelper.setPrivateValue(RandomState.class, random, replacement, "f_224548_");
            if (random.router() != replacement) throw new IllegalStateException("Router installation did not stick");
            STATES.put(level, new State("installed", columns, failed));
            LogUtils.getLogger().info("Coastal terrain prototype installed for the Overworld; legacy pools and spires suspended");
        } catch (RuntimeException ex) {
            failed.set(true);
            skipped(level, "router adapter failed: " + ex.getClass().getSimpleName());
            LogUtils.getLogger().error("Could not install coastal terrain prototype", ex);
        }
    }
    private static boolean version(String mod, String expected) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString().equals(expected)).orElse(false);
    }
    private static void skipped(ServerLevel level, String reason) {
        STATES.put(level, new State(reason, null, new AtomicBoolean(false)));
        LogUtils.getLogger().info("Coastal terrain prototype inactive: {}", reason);
    }
    public static boolean installed(ServerLevel level) {
        State state = STATES.get(level);
        // Keep legacy geometry suspended even after a runtime failure, avoiding mixed generators.
        return state != null && state.columns() != null;
    }
    public static JsonObject status(ServerLevel level) {
        State state = STATES.get(level);
        JsonObject result = new JsonObject();
        result.addProperty("adapter", "minecraft-1.20.1-runtime-router");
        result.addProperty("status", state == null ? "not applicable / not initialized" : state.reason());
        if (state != null) {
            result.addProperty("samplingFailed", state.failed().get());
            result.addProperty("routerStillWrapped", level.getChunkSource().randomState().router().finalDensity() instanceof CoastalDensity);
            if (state.columns() != null) {
                result.addProperty("plannedColumnCacheMisses", state.columns().plannedColumns());
                result.addProperty("eligibleColumnCacheMisses", state.columns().eligibleColumns());
            }
        }
        return result;
    }
}

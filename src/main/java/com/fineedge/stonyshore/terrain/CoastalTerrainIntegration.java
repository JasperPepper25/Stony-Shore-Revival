package com.fineedge.stonyshore.terrain;

import com.fineedge.stonyshore.ShoreConfig;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.ModList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Minecraft 1.20.1 adapter, installed before Forge begins preparing the Overworld spawn. */
public final class CoastalTerrainIntegration {
    private record State(String reason, NativeCoastalModel columns, AtomicBoolean failed, DensityFunction baselineFinal) {}
    private static final Map<RandomState,State> RANDOM_STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerLevel, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private static Field aquiferField;
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
        var finalNodes = nodes(random.router().finalDensity());
        var initialNodes = nodes(random.router().initialDensityWithoutJaggedness());
        if (finalNodes.size()!=1 || initialNodes.size()!=1) {
            skipped(level,"expected one native coastal node in each density field; found "
                + finalNodes.size()+" final / "+initialNodes.size()+" preliminary"); return;
        }
        var sampler = random.sampler();
        var source = generator.getBiomeSource();
        AtomicBoolean failed = new AtomicBoolean();
        NativeCoastalModel columns = new NativeCoastalModel(level.getSeed(),63,level.getMaxBuildHeight(),
            (x,y,z) -> initialNodes.get(0).input().compute(new DensityFunction.SinglePointContext(x,y,z)),
            (x,z) -> source.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(65),QuartPos.fromBlock(z),sampler).is(Biomes.STONY_SHORE),
            (x,z) -> source.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(65),QuartPos.fromBlock(z),sampler).is(BiomeTags.IS_OCEAN),
            (x,y,z) -> source.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z),sampler).is(Biomes.STONY_SHORE),
            new CoastalShape.Options(ShoreConfig.LANDFORMS.get(),ShoreConfig.POOLS.get(),ShoreConfig.ARCHES.get(),
                ShoreConfig.SANDY_SHELVES.get(),ShoreConfig.BEACH_FREQUENCY.get()));
        finalNodes.forEach(node -> node.bind(columns,failed));
        initialNodes.forEach(node -> node.bind(columns,failed));
        State state=new State("installed",columns,failed,finalNodes.get(0).input());
        STATES.put(level,state);RANDOM_STATES.put(random,state);
        LogUtils.getLogger().info("Native coastal graph active: Lithostitched pre-seeding injection; legacy pools and spires suspended");
    }
    private static java.util.List<NativeCoastalDensity> nodes(DensityFunction function) {
        var result=new java.util.ArrayList<NativeCoastalDensity>();
        function.mapAll(new NativeCoastalDensity.Observer() {
            public void inspect(NativeCoastalDensity node) { result.add(node); }
            public DensityFunction apply(DensityFunction value) { return value; }
        });
        return result;
    }

    private static boolean version(String mod, String expected) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString().equals(expected)).orElse(false);
    }
    private static void skipped(ServerLevel level, String reason) {
        STATES.put(level, new State(reason, null, new AtomicBoolean(false),null));
        LogUtils.getLogger().info("Coastal terrain prototype inactive: {}", reason);
    }
    public static boolean installed(ServerLevel level) {
        State state = STATES.get(level);
        // Keep legacy geometry suspended even after a runtime failure, avoiding mixed generators.
        return state != null && state.columns() != null;
    }
    /** Called at NoiseChunk construction, before any block generation or fluid decisions. */
    public static void attachAquifer(NoiseChunk chunk, RandomState random, Blender blender, Aquifer.FluidPicker fluids) {
        State state=RANDOM_STATES.get(random);
        if(blender!=Blender.empty() || state==null || state.failed().get())return;
        try {
            Field field = aquiferField();
            Aquifer original = (Aquifer) field.get(chunk);
            if (original instanceof CoastalAquifer) return;
            field.set(chunk, new CoastalAquifer(original, fluids, state.columns(), state.failed()));
            state.columns().aquiferAttached();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            if (state.failed().compareAndSet(false, true))
                LogUtils.getLogger().error("Coastal adapter disabled: could not attach shallow-water handling", ex);
        }
    }
    private static synchronized Field aquiferField() {
        if (aquiferField == null) {
            // One typed field in the pinned 1.20.1 class. This avoids another obfuscated-name
            // dependency; fail closed if a future layout is ambiguous. No global aquifer edit.
            var fields = Arrays.stream(NoiseChunk.class.getDeclaredFields())
                .filter(f -> f.getType() == Aquifer.class).toList();
            if (fields.size() != 1) throw new IllegalStateException("Unexpected NoiseChunk aquifer layout");
            aquiferField = fields.get(0);
            aquiferField.setAccessible(true);
        }
        return aquiferField;
    }
    public static CoastalColumnSampler.Column column(ServerLevel level, int x, int z) {
        State state = STATES.get(level);
        if (state == null || state.columns() == null || state.failed().get()) return null;
        try { return state.columns().column(x, z); }
        catch (RuntimeException ex) {
            if (state.failed().compareAndSet(false, true)) LogUtils.getLogger().error("Coastal column sampling failed", ex);
            return null;
        }
    }
    public static NativeCoastalModel sampler(ServerLevel level) {
        State state=STATES.get(level);
        return state==null || state.failed().get() ? null : state.columns();
    }
    public static double sandCover(ServerLevel level,int x,int z,int floor) {
        var sampler=sampler(level);if(sampler==null) return 0;
        try { return sampler.sandCover(x,z,floor); }
        catch(RuntimeException ex) { disable(level,ex);return 0; }
    }
    public static CoastalLandforms.Arch arch(ServerLevel level,int x,int z) {
        var sampler=sampler(level);if(sampler==null) return null;
        try { return sampler.arch(x,z); }
        catch(RuntimeException ex) { disable(level,ex);return null; }
    }
    public static CoastalLandforms.Overhang overhang(ServerLevel level,int x,int z) {
        var sampler=sampler(level);if(sampler==null)return null;
        try { return sampler.overhang(x,z); }
        catch(RuntimeException ex) {disable(level,ex);return null;}
    }
    private static void disable(ServerLevel level,RuntimeException ex) {
        State state=STATES.get(level);
        if(state!=null && state.failed().compareAndSet(false,true)) LogUtils.getLogger().error("Coastal landform sampling disabled",ex);
    }
    public static DensityFunction baselineFinal(ServerLevel level) {
        State state=STATES.get(level);return state==null?null:state.baselineFinal();
    }
    public static JsonObject status(ServerLevel level) {
        State state = STATES.get(level);
        JsonObject result = new JsonObject();
        result.addProperty("adapter", "lithostitched-1.4.11-preseed-native-density");
        result.addProperty("injectionPriority",1100);
        result.addProperty("runtimeRouterReplacement",false);
        result.addProperty("densityConstruction","3D coastal solid/air field blended with loaded pack density");
        result.addProperty("biomePlacementModified",false);
        result.addProperty("regionalLandforms", ShoreConfig.LANDFORMS.get());
        result.addProperty("coastalArches", ShoreConfig.ARCHES.get());
        result.addProperty("status", state == null ? "not applicable / not initialized" : state.reason());
        if (state != null) {
            result.addProperty("samplingFailed", state.failed().get());
            result.addProperty("routerStillWrapped", !nodes(level.getChunkSource().randomState().router().finalDensity()).isEmpty());
            if (state.columns() != null) {
                result.addProperty("plannedColumnCacheMisses", state.columns().plannedColumns());
                result.addProperty("eligibleColumnCacheMisses", state.columns().eligibleColumns());
                result.addProperty("shallowWaterAquiferAttachments", state.columns().aquiferAttachments());
                result.addProperty("coastalWaterDecisions", state.columns().waterDecisions());
                JsonObject plans=new JsonObject();state.columns().landformStats().forEach(plans::addProperty);
                result.add("landformPlanningEvaluations",plans);
            }
        }
        return result;
    }
}

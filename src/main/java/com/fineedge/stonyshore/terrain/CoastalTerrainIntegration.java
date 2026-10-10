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
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
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
    private record State(String reason, NativeCoastalModel columns, AtomicBoolean failed, DensityFunction baselineFinal,
                         String biomeReason) {}
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
            || generator.getBiomeSource().getClass() != MultiNoiseBiomeSource.class) {
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
        var surfaceSampler=CoastalBiomeIntegration.surfaceProjection(sampler);
        var source = (MultiNoiseBiomeSource)generator.getBiomeSource();
        var rockyBiomes = CoastalBiomeIntegration.configuredRockyBiomes();
        AtomicBoolean failed = new AtomicBoolean();
        NativeCoastalModel columns = new NativeCoastalModel(level.getSeed(),63,level.getMaxBuildHeight(),
            (x,y,z) -> initialNodes.get(0).input().compute(new DensityFunction.SinglePointContext(x,y,z)),
            (x,z) -> CoastalBiomeIntegration.rocky(surfaceBiome(source,surfaceSampler,x,z),rockyBiomes),
            (x,z) -> surfaceBiome(source,surfaceSampler,x,z).is(BiomeTags.IS_OCEAN),
            (x,y,z) -> CoastalBiomeIntegration.rocky(CoastalBiomeIntegration.original(source,sampler,x,y,z),rockyBiomes),
            new CoastalShape.Options(ShoreConfig.LANDFORMS.get(),ShoreConfig.POOLS.get(),ShoreConfig.ARCHES.get(),
                ShoreConfig.SANDY_SHELVES.get(),ShoreConfig.BEACH_FREQUENCY.get()),
            (x,y,z)->finalNodes.get(0).input().compute(new DensityFunction.SinglePointContext(x,y,z)),
            ShoreConfig.COASTAL_INLAND_WIDTH.get());
        finalNodes.forEach(node -> node.bind(columns,failed));
        initialNodes.forEach(node -> node.bind(columns,failed));
        String biomeReason = !ShoreConfig.COASTAL_BIOMES.get() ? "disabled by config"
            : ModList.get().isLoaded("terrablender") && !version("terrablender","3.0.1.10")
                ? "unsupported TerraBlender version; coordinate adapter inactive"
                : "pending target validation";
        State state=new State("installed",columns,failed,finalNodes.get(0).input(),biomeReason);
        STATES.put(level,state);RANDOM_STATES.put(random,state);
        if (biomeReason.equals("pending target validation")) {
            try {
                boolean bound=CoastalBiomeIntegration.bind(source,sampler,columns,failed,rockyBiomes,ex->disable(level,ex));
                state=new State("installed",columns,failed,finalNodes.get(0).input(),bound?"installed":"stony shore absent from original possible-biome set");
            } catch (RuntimeException ex) {
                disable(level,ex);
                state=new State("installed",columns,failed,finalNodes.get(0).input(),"target validation failed; coastal adapter disabled");
            }
            STATES.put(level,state);RANDOM_STATES.put(random,state);
        }
        LogUtils.getLogger().info("Coastal surface biome coordination: {}",state.biomeReason());
        LogUtils.getLogger().info("Native coastal graph active: Lithostitched pre-seeding injection; legacy pools and spires suspended");
    }
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        STATES.remove(level);
        RandomState random=level.getChunkSource().randomState();
        RANDOM_STATES.remove(random);
        if (level.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource source)
            CoastalBiomeIntegration.unbind(source,random.sampler());
    }
    private static java.util.List<NativeCoastalDensity> nodes(DensityFunction function) {
        var result=new java.util.ArrayList<NativeCoastalDensity>();
        function.mapAll(new NativeCoastalDensity.Observer() {
            public void inspect(NativeCoastalDensity node) { result.add(node); }
            public DensityFunction apply(DensityFunction value) { return value; }
        });
        return result;
    }

    private static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> surfaceBiome(
            MultiNoiseBiomeSource source,Climate.Sampler sampler,int x,int z) {
        // The once-per-level sampler projects depth to zero, but keeps positional region selection.
        return CoastalBiomeIntegration.original(source,sampler,x,65,z);
    }
    public static boolean shoreColumn(ServerLevel level,int x,int z) {
        var model=sampler(level);return model!=null && model.shoreColumn(x,z);
    }
    private static boolean version(String mod, String expected) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString().equals(expected)).orElse(false);
    }
    private static void skipped(ServerLevel level, String reason) {
        STATES.put(level, new State(reason, null, new AtomicBoolean(false),null,"terrain adapter inactive"));
        LogUtils.getLogger().info("Coastal terrain prototype inactive: {}", reason);
    }
    public static boolean installed(ServerLevel level) {
        State state = STATES.get(level);
        // Keep legacy geometry suspended even after a runtime failure, avoiding mixed generators.
        return state != null && state.columns() != null;
    }
    /** Preserve retrogen/blended resolvers; coordinate only the active world's fresh biome fill. */
    public static BiomeResolver coordinateBiomeFill(RandomState random, Blender blender,
                                                    BiomeSource source, BiomeResolver delegate) {
        State state=RANDOM_STATES.get(random);
        if(blender!=Blender.empty() || state==null || state.columns()==null || state.failed().get())return delegate;
        return CoastalBiomeIntegration.coordinateFill(source,random.sampler(),delegate);
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
        result.addProperty("densityConstruction","continuous physical coastal corridor; unmodified terrain support; cliff-preserving beach and seabed profile; attached seaward rock volumes");
        result.addProperty("oceanTerrainReachBlocks",CoastalBeachProfile.OCEAN_REACH);
        result.addProperty("poolConstruction","terrace-fitted irregular basins; up to two per planning region; validated wet area and floor");
        result.addProperty("inlandTransitionWidthBlocks",ShoreConfig.COASTAL_INLAND_WIDTH.get());
        result.addProperty("quarkStoneExclusion","per-destination jasper/shale/limestone in shore biome or shore surface columns; optional Quark mixin");
        result.addProperty("quarkGeneratorsObserved",com.fineedge.stonyshore.generation.QuarkStonePolicy.generatorsObserved());
        result.addProperty("quarkClusterHookObserved",com.fineedge.stonyshore.generation.QuarkStonePolicy.hookObserved());
        result.addProperty("quarkStonePlacementsRejectedProcessTotal",com.fineedge.stonyshore.generation.QuarkStonePolicy.rejectedPlacements());
        result.addProperty("biomePlacementModified",false);
        result.addProperty("biomeCoordinationConfig",ShoreConfig.COASTAL_BIOMES.get());
        result.addProperty("biomeCoordinationStatus",state==null?"not initialized":state.biomeReason());
        if (level.getChunkSource().getGenerator().getBiomeSource() instanceof MultiNoiseBiomeSource source)
            CoastalBiomeIntegration.status(result,source,level.getChunkSource().randomState().sampler());
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

package com.fineedge.stonyshore.terrain;

import com.fineedge.stonyshore.ShoreConfig;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraftforge.common.Tags;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Runtime adapter on the existing source; its codec and possible-biome set are unchanged. */
public final class CoastalBiomeIntegration {
    /** Geometry is horizontal; original biome protection/temperature remain per query. */
    private record ColumnPolicy(CoastalBiomePolicy.Context warm,CoastalBiomePolicy.Context cold) {
        private static final ColumnPolicy OUTSIDE=new ColumnPolicy(null,null);
    }
    private static final class DelegateState { int depth; }
    private record Binding(NativeCoastalModel model, AtomicBoolean failed, Set<ResourceLocation> rocky,
                           Holder<Biome> stone, Holder<Biome> beach, Holder<Biome> snow,
                           LongAdder hookCalls, LongAdder fillCalls, LongAdder queries, LongAdder changed,
                           Consumer<RuntimeException> fail, AtomicBoolean active,
                           BoundedLongCache<ColumnPolicy> columns) {}
    private static final Map<MultiNoiseBiomeSource, Map<Climate.Sampler, Binding>> SOURCES =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final LongAdder HOOK_CALLS = new LongAdder();
    // A small thread-owned guard with no source, sampler or world references.
    private static final ThreadLocal<DelegateState> DELEGATE_QUERY_DEPTH = ThreadLocal.withInitial(DelegateState::new);
    private CoastalBiomeIntegration() {}

    public static Set<ResourceLocation> configuredRockyBiomes() {
        return ShoreConfig.ROCKY_COAST_BIOMES.get().stream().map(ResourceLocation::new).collect(Collectors.toUnmodifiableSet());
    }
    public static boolean rocky(Holder<Biome> biome, Set<ResourceLocation> ids) {
        return biome.is(Biomes.STONY_SHORE) || biome.unwrapKey().map(k -> ids.contains(k.location())).orElse(false);
    }
    /** Positional delegate query: TerraBlender's coordinate HEAD hook must still run. */
    public static Holder<Biome> original(BiomeSource source, Climate.Sampler sampler, int x, int y, int z) {
        DelegateState state=DELEGATE_QUERY_DEPTH.get();state.depth++;
        try { return source.getNoiseBiome(QuartPos.fromBlock(x),QuartPos.fromBlock(y),QuartPos.fromBlock(z),sampler); }
        finally { state.depth--; }
    }
    /** Shared ownership boundary for biome coordination and material continuation. */
    public static boolean protectedBiome(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_RIVER) || biome.is(Tags.Biomes.IS_CAVE)
            || biome.is(Tags.Biomes.IS_UNDERGROUND) || biome.is(Biomes.LUSH_CAVES)
            || biome.is(Biomes.DRIPSTONE_CAVES) || biome.is(Biomes.DEEP_DARK);
    }
    /** Construct once per level. Coordinates still select TerraBlender's region; only depth is projected. */
    public static Climate.Sampler surfaceProjection(Climate.Sampler sampler) {
        return new Climate.Sampler(sampler.temperature(),sampler.humidity(),sampler.continentalness(),
            sampler.erosion(),net.minecraft.world.level.levelgen.DensityFunctions.zero(),sampler.weirdness(),sampler.spawnTarget());
    }
    private static <T> T delegateQuery(Supplier<T> query) {
        DelegateState state=DELEGATE_QUERY_DEPTH.get();state.depth++;
        try { return query.get(); }
        finally { state.depth--; }
    }
    /** Null means let the original method and its other mixins execute normally. */
    public static Holder<Biome> intercept(MultiNoiseBiomeSource source, Climate.Sampler sampler,int qx,int qy,int qz) {
        return intercept(source,sampler,qx,qy,qz,() -> source.getNoiseBiome(qx,qy,qz,sampler));
    }
    static Holder<Biome> intercept(MultiNoiseBiomeSource source, Climate.Sampler sampler,int qx,int qy,int qz,
                                  Supplier<Holder<Biome>> positionalDelegate) {
        if(DELEGATE_QUERY_DEPTH.get().depth>0)return null;
        Binding binding=binding(source,sampler);
        if(binding==null || binding.failed.get() || QuartPos.toBlock(qy)<binding.model.seaLevel()-8)return null;
        HOOK_CALLS.increment();
        binding.hookCalls.increment();
        Holder<Biome> original=delegateQuery(positionalDelegate);
        return adjust(current(source,sampler,binding),qx,qy,qz,original);
    }
    /**
     * Chunk biome fill uses a NoiseChunk-cached sampler rather than the world's canonical sampler.
     * Retain that exact delegate sampler, but select the policy by canonical world identity. No
     * cached sampler aliases are retained beyond the fill call or added to global maps.
     */
    public static BiomeResolver coordinateFill(BiomeSource source, Climate.Sampler canonical, BiomeResolver delegate) {
        if (!(source instanceof MultiNoiseBiomeSource multi)) return delegate;
        Binding binding=binding(multi,canonical);
        if(binding==null || binding.failed.get())return delegate;
        binding.fillCalls.increment();
        return (qx,qy,qz,cached)->{
            Holder<Biome> original;
            DelegateState state=DELEGATE_QUERY_DEPTH.get();state.depth++;
            try { original=delegate.getNoiseBiome(qx,qy,qz,cached); }
            finally { state.depth--; }
            return adjust(current(multi,canonical,binding),qx,qy,qz,original);
        };
    }
    public static boolean bind(MultiNoiseBiomeSource source, Climate.Sampler sampler, NativeCoastalModel model,
                               AtomicBoolean failed, Set<ResourceLocation> rocky, Consumer<RuntimeException> fail) {
        if (!ShoreConfig.COASTAL_BIOMES.get()) return false;
        var possible = source.possibleBiomes();
        Holder<Biome> stone = possible.stream().filter(b -> b.is(Biomes.STONY_SHORE)).findFirst().orElse(null);
        if (stone == null) return false; // Never emit a biome missing from feature-order planning.
        Holder<Biome> beach = possible.stream().filter(b -> b.is(Biomes.BEACH)).findFirst().orElse(null);
        Holder<Biome> snow = possible.stream().filter(b -> b.is(Biomes.SNOWY_BEACH)).findFirst().orElse(null);
        var binding = new Binding(model, failed, rocky, stone, beach, snow,
            new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder(), fail,
            new AtomicBoolean(true),new BoundedLongCache<>(2048,16));
        synchronized (SOURCES) {
            var samplers=SOURCES.computeIfAbsent(source, ignored -> new IdentityHashMap<>());
            var previous=samplers.get(sampler);
            if(previous!=null)previous.active.set(false);
            samplers.put(sampler,binding);
        }
        return true;
    }
    public static Holder<Biome> adjust(MultiNoiseBiomeSource source, Climate.Sampler sampler,
                                       int qx, int qy, int qz, Holder<Biome> original) {
        return adjust(binding(source,sampler),qx,qy,qz,original);
    }
    private static ColumnPolicy plan(Binding binding,int x,int z) {
        var ground=binding.model.ground(x,z);
        if(ground.mask()<.55)return ColumnPolicy.OUTSIDE;
        var column=binding.model.detail(x,z);
        var warm=new CoastalBiomePolicy.Context(column.original(),column.surface(),column.mask(),column.sand(),
            column.pool()!=null,column.arch()!=null || column.overhang()!=null,false,false);
        var cold=new CoastalBiomePolicy.Context(column.original(),column.surface(),column.mask(),column.sand(),
            column.pool()!=null,column.arch()!=null || column.overhang()!=null,false,true);
        return new ColumnPolicy(warm,cold);
    }
    private static Holder<Biome> adjust(Binding binding,int qx,int qy,int qz,Holder<Biome> original) {
        if (binding == null || !binding.active.get() || binding.failed.get() || original == null) return original;
        binding.queries.increment();
        // Respect cave and river selections from both vanilla and tagged biome providers.
        if (protectedBiome(original)) return original;
        int x = QuartPos.toBlock(qx) + 2, y = QuartPos.toBlock(qy), z = QuartPos.toBlock(qz) + 2;
        if (y < binding.model.seaLevel() - 8) return original;
        try {
            var policy=binding.columns.get(CoastalShape.key(qx,qz),key->plan(binding,x,z));
            if(policy==ColumnPolicy.OUTSIDE)return original;
            boolean cold=original.value().getBaseTemperature()<=.15F || original.is(Tags.Biomes.IS_SNOWY);
            var context=cold?policy.cold:policy.warm;
            var choice = CoastalBiomePolicy.choose(context, y, binding.model.seaLevel());
            Holder<Biome> selected = switch (choice) {
                case ORIGINAL -> original;
                case ROCKY_SHORE -> rocky(original, binding.rocky) ? original : binding.stone;
                case BEACH -> original.is(BiomeTags.IS_BEACH) ? original : binding.beach != null ? binding.beach : binding.stone;
                case SNOWY_BEACH -> original.is(BiomeTags.IS_BEACH) ? original : binding.snow != null ? binding.snow : binding.stone;
            };
            if (selected != original && !selected.equals(original)) binding.changed.increment();
            return selected;
        } catch (RuntimeException ex) {
            binding.fail.accept(ex);
            return original;
        }
    }
    private static Binding binding(MultiNoiseBiomeSource source, Climate.Sampler sampler) {
        synchronized (SOURCES) {
            var samplers = SOURCES.get(source);
            return samplers == null ? null : samplers.get(sampler);
        }
    }
    /** A retained fill resolver follows replacement ownership and stops after unload. */
    private static Binding current(MultiNoiseBiomeSource source,Climate.Sampler sampler,Binding expected) {
        return expected.active.get()?expected:binding(source,sampler);
    }
    public static void unbind(MultiNoiseBiomeSource source, Climate.Sampler sampler) {
        synchronized (SOURCES) {
            var samplers = SOURCES.get(source);
            if (samplers == null) return;
            var removed=samplers.remove(sampler);
            if(removed!=null)removed.active.set(false);
            if (samplers.isEmpty()) SOURCES.remove(source);
        }
    }
    public static void status(JsonObject result, MultiNoiseBiomeSource source, Climate.Sampler sampler) {
        var b = binding(source, sampler);
        result.addProperty("biomeCoordinationEnabled", b != null && !b.failed.get());
        result.addProperty("biomePlacementModified", b != null && b.changed.sum() > 0);
        result.addProperty("coastalBiomeHookObserved", b != null && b.hookCalls.sum() > 0);
        result.addProperty("biomeFillHookObserved", b != null && b.fillCalls.sum() > 0);
        result.addProperty("biomePlacementScope", "geometry-coordinated surface coastal band; original source and possible-biome set retained");
        result.addProperty("biomeCoordinateHookCallsProcessTotal", HOOK_CALLS.sum());
        result.addProperty("biomeCodecChanged", false);
        result.addProperty("climateNoiseChanged", false);
        if (b != null) {
            result.addProperty("coastalBiomeHookCalls", b.hookCalls.sum());
            result.addProperty("biomeFillHookCalls", b.fillCalls.sum());
            result.addProperty("coastalBiomeQueries", b.queries.sum());
            result.addProperty("coastalBiomeSelectionsChanged", b.changed.sum());
            result.addProperty("biomeColumnPolicyCacheHits",b.columns.hits());
            result.addProperty("biomeColumnPolicyCacheMisses",b.columns.misses());
            result.addProperty("biomeColumnPolicyCacheLocalHits",b.columns.localHits());
            result.addProperty("beachBiomeAvailable", b.beach != null);
            result.addProperty("snowyBeachBiomeAvailable", b.snow != null);
        }
    }
}

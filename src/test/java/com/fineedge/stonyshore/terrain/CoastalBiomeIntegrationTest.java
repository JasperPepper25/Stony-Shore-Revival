package com.fineedge.stonyshore.terrain;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.fineedge.stonyshore.ShoreConfig;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BiomeTags;
import net.minecraftforge.common.Tags;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Registry and source codec checks; actual Mixin transformation is covered by runtime smoke. */
class CoastalBiomeIntegrationTest {
    @BeforeAll static void setup() {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        ShoreConfig.SPEC.setConfig(CommentedConfig.inMemory());
    }
    private record Fixture(MultiNoiseBiomeSource source, MappedRegistry<Biome> registry,
                           Holder<Biome> plain, Holder<Biome> stone, Holder<Biome> cave,
                           Climate.Sampler sampler, NativeCoastalModel model) {}
    private static Biome biome() {
        return new Biome.BiomeBuilder().hasPrecipitation(true).temperature(.7F).downfall(.5F)
            .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
            .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build();
    }
    private static Fixture fixture(boolean stoneAvailable) {
        var registry=new MappedRegistry<Biome>(Registries.BIOME,Lifecycle.stable());
        var plain=registry.register(Biomes.PLAINS,biome(),Lifecycle.stable());
        var stone=registry.register(Biomes.STONY_SHORE,biome(),Lifecycle.stable());
        var beach=registry.register(Biomes.BEACH,biome(),Lifecycle.stable());
        var cave=registry.register(Biomes.LUSH_CAVES,biome(),Lifecycle.stable());
        registry.freeze();
        List<Pair<Climate.ParameterPoint,Holder<Biome>>> entries=stoneAvailable ? List.of(
            Pair.of(Climate.parameters(0,0,0,0,0,0,0),plain),
            Pair.of(Climate.parameters(1,0,0,0,0,0,0),stone),
            Pair.of(Climate.parameters(-1,0,0,0,0,0,0),beach),
            Pair.of(Climate.parameters(2,0,0,0,0,0,0),cave)) : List.of(
            Pair.of(Climate.parameters(0,0,0,0,0,0,0),plain));
        var source=MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(entries));
        var zero=DensityFunctions.zero();
        var sampler=new Climate.Sampler(zero,zero,zero,zero,zero,zero,List.of());
        CoastalColumnSampler.Terrain terrain=(x,y,z)->(110+Math.min(0,x)*1.3-y)*.15;
        var model=new NativeCoastalModel(41,63,192,terrain,(x,z)->x>=-40 && x<100,
            (x,z)->x< -40,(x,y,z)->x>=-40 && x<100,
            new CoastalShape.Options(false,false,false,false,0),terrain,96);
        return new Fixture(source,registry,plain,stone,cave,sampler,model);
    }
    private static void bind(Fixture f) {
        assertTrue(CoastalBiomeIntegration.bind(f.source,f.sampler,f.model,new AtomicBoolean(),Set.of(),ex->fail(ex)));
    }
    @Test void runtimeBindingLeavesSerializedSourceAndPossibleBiomeSetUntouchedAndRebindsAfterDecode() {
        var f=fixture(true);
        var ops=RegistryOps.create(JsonOps.INSTANCE,HolderLookup.Provider.create(Stream.of(f.registry.asLookup())));
        var before=MultiNoiseBiomeSource.CODEC.encodeStart(ops,f.source).result().orElseThrow();
        var possible=Set.copyOf(f.source.possibleBiomes());
        try {
            bind(f);
            assertEquals(before,MultiNoiseBiomeSource.CODEC.encodeStart(ops,f.source).result().orElseThrow());
            assertEquals(possible,f.source.possibleBiomes());
            assertSame(f.plain,CoastalBiomeIntegration.original(f.source,f.sampler,32,108,0));
            assertSame(f.stone,CoastalBiomeIntegration.adjust(f.source,f.sampler,8,27,0,f.plain));
            assertSame(f.plain,CoastalBiomeIntegration.adjust(f.source,f.sampler,8,0,0,f.plain));
            var decoded=MultiNoiseBiomeSource.CODEC.parse(ops,before).result().orElseThrow();
            try {
                assertTrue(CoastalBiomeIntegration.bind(decoded,f.sampler,f.model,new AtomicBoolean(),Set.of(),ex->fail(ex)));
                assertSame(f.stone,CoastalBiomeIntegration.adjust(decoded,f.sampler,8,27,0,f.plain));
            } finally {CoastalBiomeIntegration.unbind(decoded,f.sampler);}
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void samplerIdentitySeparatesWorldContextsAndUnloadingRestoresTheDelegate() {
        var f=fixture(true);
        try {
            bind(f);
            var zero=DensityFunctions.zero();
            var another=new Climate.Sampler(zero,zero,zero,zero,zero,zero,List.of());
            assertEquals(f.sampler,another,"fixture samplers compare equal, so a value-keyed binding would cross worlds");
            assertSame(f.plain,CoastalBiomeIntegration.adjust(f.source,another,8,27,0,f.plain));
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
        assertSame(f.plain,CoastalBiomeIntegration.adjust(f.source,f.sampler,8,27,0,f.plain));
    }
    @Test void refusesToEmitTargetsAbsentFromOriginalFeaturePlanning() {
        var f=fixture(false);
        assertFalse(CoastalBiomeIntegration.bind(f.source,f.sampler,f.model,new AtomicBoolean(),Set.of(),ex->fail(ex)));
        assertSame(f.plain,CoastalBiomeIntegration.adjust(f.source,f.sampler,8,27,0,f.plain));
    }
    @Test void guardedHeadLetsACancellingPositionalDelegateRunExactlyOnceAndRetainsItsProtectedSelection() {
        var f=fixture(true);var positionalCalls=new AtomicInteger();
        try {
            bind(f);
            var adjusted=CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{
                positionalCalls.incrementAndGet();
                // The recursive coordinate method first reaches our HEAD again. Its null
                // response lets a TerraBlender-style cancelling HEAD choose the region.
                assertNull(CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{
                    fail("the guarded HEAD must not query its delegate recursively");return f.plain;
                }));
                return f.plain;
            });
            assertSame(f.stone,adjusted);
            assertSame(f.cave,CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{
                positionalCalls.incrementAndGet();return f.cave;
            }),"the positional provider's cave result must survive the coastal policy");
            assertEquals(2,positionalCalls.get());
            var status=new JsonObject();CoastalBiomeIntegration.status(status,f.source,f.sampler);
            assertTrue(status.get("coastalBiomeHookObserved").getAsBoolean());
            assertEquals(2,status.get("coastalBiomeHookCalls").getAsLong());
            assertEquals(2,status.get("coastalBiomeQueries").getAsLong());
            assertEquals(1,status.get("coastalBiomeSelectionsChanged").getAsLong());
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void deepUnboundAndUnloadingQueriesLeaveTheOriginalMethodInControlWithoutCountingHooks() {
        var f=fixture(true);var delegated=new AtomicInteger();
        assertNull(CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{
            delegated.incrementAndGet();return f.plain;
        }));
        try {
            bind(f);
            assertNull(CoastalBiomeIntegration.intercept(f.source,f.sampler,8,0,0,()->{
                delegated.incrementAndGet();return f.plain;
            }));
            // Direct policy checks are not evidence that a Mixin HEAD ran in game.
            assertSame(f.stone,CoastalBiomeIntegration.adjust(f.source,f.sampler,8,27,0,f.plain));
            var status=new JsonObject();CoastalBiomeIntegration.status(status,f.source,f.sampler);
            assertFalse(status.get("coastalBiomeHookObserved").getAsBoolean());
            assertEquals(0,status.get("coastalBiomeHookCalls").getAsLong());
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
        assertNull(CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{
            delegated.incrementAndGet();return f.plain;
        }));
        assertEquals(0,delegated.get());
    }
    @Test void delegateFailureRestoresTheThreadGuardForFollowingCoordinates() {
        var f=fixture(true);
        try {
            bind(f);
            var exception=new IllegalStateException("simulated positional provider failure");
            assertSame(exception,assertThrows(IllegalStateException.class,()->
                CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->{throw exception;})));
            assertSame(f.stone,CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->f.plain));
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void surfaceProjectionRetainsClimateFieldsAndSpawnTargetsWhileRemovingOnlyDepth() {
        var sampler=new Climate.Sampler(DensityFunctions.constant(.2),DensityFunctions.constant(.3),
            DensityFunctions.constant(.4),DensityFunctions.constant(.5),DensityFunctions.constant(.8),
            DensityFunctions.constant(.6),List.of(Climate.parameters(0,0,0,0,0,0,0)));
        var surface=CoastalBiomeIntegration.surfaceProjection(sampler);
        assertSame(sampler.temperature(),surface.temperature());
        assertSame(sampler.humidity(),surface.humidity());
        assertSame(sampler.continentalness(),surface.continentalness());
        assertSame(sampler.erosion(),surface.erosion());
        assertSame(sampler.weirdness(),surface.weirdness());
        assertSame(sampler.spawnTarget(),surface.spawnTarget());
        assertEquals(0,surface.depth().compute(new DensityFunction.SinglePointContext(12,99,-7)));
        var original=sampler.sample(3,24,-2);var projected=surface.sample(3,24,-2);
        assertEquals(original.temperature(),projected.temperature());
        assertEquals(original.humidity(),projected.humidity());
        assertEquals(original.continentalness(),projected.continentalness());
        assertEquals(original.erosion(),projected.erosion());
        assertEquals(original.weirdness(),projected.weirdness());
        assertNotEquals(0,original.depth());assertEquals(0,projected.depth());
    }
    @Test void sharedProtectionIncludesKnownCavesAndProviderRiverAndUndergroundTags() {
        var f=fixture(true);
        assertTrue(CoastalBiomeIntegration.protectedBiome(f.cave));
        assertFalse(CoastalBiomeIntegration.protectedBiome(f.plain));
        f.registry.bindTags(Map.of(BiomeTags.IS_RIVER,List.of(f.plain),Tags.Biomes.IS_UNDERGROUND,List.of(f.stone)));
        assertTrue(CoastalBiomeIntegration.protectedBiome(f.plain));
        assertTrue(CoastalBiomeIntegration.protectedBiome(f.stone));
        try {
            bind(f);
            assertSame(f.plain,CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->f.plain));
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void chunkFillUsesCanonicalWorldOwnershipButPreservesTheSuppliedCachedDelegateSampler() {
        var f=fixture(true);var zero=DensityFunctions.zero();
        var cached=new Climate.Sampler(zero,zero,zero,zero,DensityFunctions.constant(.25),zero,List.of());
        var calls=new AtomicInteger();
        BiomeResolver delegate=(qx,qy,qz,sampler)->{
            assertSame(cached,sampler,"chunk-specific climate caches must reach the positional provider unchanged");
            assertEquals(8,qx);assertEquals(27,qy);assertEquals(0,qz);calls.incrementAndGet();
            assertNull(CoastalBiomeIntegration.intercept(f.source,f.sampler,qx,qy,qz,()->{
                fail("the fill delegate must bypass coordinate adjustment until the fill policy runs");return f.plain;
            }));
            return f.plain;
        };
        try {
            bind(f);
            var resolver=CoastalBiomeIntegration.coordinateFill(f.source,f.sampler,delegate);
            assertSame(f.stone,resolver.getNoiseBiome(8,27,0,cached));
            assertEquals(1,calls.get());
            var status=new JsonObject();CoastalBiomeIntegration.status(status,f.source,f.sampler);
            assertTrue(status.get("biomeFillHookObserved").getAsBoolean());
            assertEquals(1,status.get("biomeFillHookCalls").getAsLong());
            assertFalse(status.get("coastalBiomeHookObserved").getAsBoolean(),
                "fill interception and the direct coordinate HEAD are separate runtime evidence");
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void chunkFillRetainsProtectedDeepAndOutsideSelectionsAndStopsAdjustingAfterUnload() {
        var f=fixture(true);
        try {
            bind(f);
            var plain=CoastalBiomeIntegration.coordinateFill(f.source,f.sampler,(qx,qy,qz,s)->f.plain);
            var cave=CoastalBiomeIntegration.coordinateFill(f.source,f.sampler,(qx,qy,qz,s)->f.cave);
            assertSame(f.plain,plain.getNoiseBiome(8,0,0,f.sampler));
            assertSame(f.cave,cave.getNoiseBiome(8,27,0,f.sampler));
            assertSame(f.plain,plain.getNoiseBiome(500,27,0,f.sampler));
            assertSame(f.stone,plain.getNoiseBiome(8,27,0,f.sampler));
            CoastalBiomeIntegration.unbind(f.source,f.sampler);
            assertSame(f.plain,plain.getNoiseBiome(8,27,0,f.sampler));
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void chunkFillDelegateFailureRestoresGuardForTheNextCoordinateQuery() {
        var f=fixture(true);
        try {
            bind(f);
            var error=new IllegalArgumentException("cached provider failed");
            var resolver=CoastalBiomeIntegration.coordinateFill(f.source,f.sampler,(qx,qy,qz,s)->{throw error;});
            assertSame(error,assertThrows(IllegalArgumentException.class,()->resolver.getNoiseBiome(8,27,0,f.sampler)));
            assertSame(f.stone,CoastalBiomeIntegration.intercept(f.source,f.sampler,8,27,0,()->f.plain));
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
    @Test void inactiveAndEqualButDifferentWorldSamplersRetainTheOriginalFillResolver() {
        var f=fixture(true);BiomeResolver delegate=(qx,qy,qz,s)->f.plain;
        assertSame(delegate,CoastalBiomeIntegration.coordinateFill(f.source,f.sampler,delegate));
        try {
            bind(f);
            var zero=DensityFunctions.zero();
            var otherWorld=new Climate.Sampler(zero,zero,zero,zero,zero,zero,List.of());
            assertEquals(f.sampler,otherWorld);
            assertSame(delegate,CoastalBiomeIntegration.coordinateFill(f.source,otherWorld,delegate));
            var status=new JsonObject();CoastalBiomeIntegration.status(status,f.source,f.sampler);
            assertFalse(status.get("biomeFillHookObserved").getAsBoolean());
        } finally {CoastalBiomeIntegration.unbind(f.source,f.sampler);}
    }
}

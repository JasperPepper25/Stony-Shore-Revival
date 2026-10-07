package com.fineedge.stonyshore.audit;

import net.minecraft.world.level.levelgen.*;

/** Unwrap only export roots. Child registry references stay keyed instead of expanding a huge DAG. */
final class AuditDensitySerialization {
    private AuditDensitySerialization() {}
    static DensityFunction expand(DensityFunction function) {
        while(function instanceof DensityFunctions.HolderHolder h)function=h.function().value();
        return function;
    }
    static NoiseGeneratorSettings expand(NoiseGeneratorSettings settings) {
        var r=settings.noiseRouter();
        var router=new NoiseRouter(expand(r.barrier()),expand(r.fluidLevelFloodedness()),expand(r.fluidLevelSpread()),
            expand(r.lava()),expand(r.temperature()),expand(r.vegetation()),expand(r.continents()),expand(r.erosion()),
            expand(r.depth()),expand(r.ridges()),expand(r.initialDensityWithoutJaggedness()),expand(r.finalDensity()),
            expand(r.veinToggle()),expand(r.veinRidged()),expand(r.veinGap()));
        return new NoiseGeneratorSettings(settings.noiseSettings(),settings.defaultBlock(),settings.defaultFluid(),
            router,settings.surfaceRule(),settings.spawnTarget(),settings.seaLevel(),settings.disableMobGeneration(),
            settings.aquifersEnabled(),settings.oreVeinsEnabled(),settings.useLegacyRandomSource());
    }
}

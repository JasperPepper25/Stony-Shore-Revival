package com.fineedge.stonyshore.audit;

import net.minecraft.world.level.levelgen.*;

/** Expand runtime holder wrappers in an export copy; never mutate the live router. */
final class AuditDensitySerialization {
    private AuditDensitySerialization() {}
    private static final DensityFunction.Visitor UNWRAP=value->{
            while(value instanceof DensityFunctions.HolderHolder holder)value=holder.function().value();
            return value;
    };
    static DensityFunction expand(DensityFunction function) { return function.mapAll(UNWRAP); }
    static NoiseGeneratorSettings expand(NoiseGeneratorSettings settings) {
        return new NoiseGeneratorSettings(settings.noiseSettings(),settings.defaultBlock(),settings.defaultFluid(),
            settings.noiseRouter().mapAll(UNWRAP),settings.surfaceRule(),settings.spawnTarget(),
            settings.seaLevel(),settings.disableMobGeneration(),settings.aquifersEnabled(),settings.oreVeinsEnabled(),settings.useLegacyRandomSource());
    }
}

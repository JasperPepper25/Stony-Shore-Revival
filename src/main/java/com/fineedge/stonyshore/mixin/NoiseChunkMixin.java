package com.fineedge.stonyshore.mixin;

import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Constructor-only hook; no overwrite of noise generation or the pack's aquifer factory. */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {
    // Constructors are not renamed. Argument/class references are remapped by the production
    // JAR renamer; there are no method/field name strings requiring a Mixin refmap.
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void stonyshore$attachWater(int cellCount, RandomState random, int x, int z,
        NoiseSettings noise, DensityFunctions.BeardifierOrMarker beard, NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluids, Blender blender, CallbackInfo ci) {
        com.fineedge.stonyshore.audit.GenerationRecording.noiseStarted(random,x,z);
        CoastalTerrainIntegration.attachAquifer((NoiseChunk) (Object) this, random, blender, fluids);
    }
}

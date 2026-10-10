package com.fineedge.stonyshore.mixin;

import com.fineedge.stonyshore.terrain.CoastalBiomeIntegration;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Retains TerraBlender's positional delegate before applying the coastal policy. */
@Mixin(value=MultiNoiseBiomeSource.class,priority=900)
public abstract class MultiNoiseBiomeSourceMixin {
    // Explicit official and SRG selectors verified against the pinned 1.20.1 jars. The
    // descriptor is essential: the TargetPoint overload must never receive this injection.
    // RETURN injection would miss TerraBlender's generated early return. The guarded inner
    // coordinate call skips this adapter while TerraBlender still chooses its positional region.
    // In the pinned Forge/Mixin pipeline, lower-priority HEAD callbacks are prepended. Runtime
    // bytecode showed priority 1100 behind TerraBlender's cancelling 1000 callback; 900 runs first.
    @Inject(method = {
        "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
        "m_203407_(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;"
    }, at = @At("HEAD"), cancellable = true, remap = false)
    private void stonyshore$coordinateCoastalBiomes(int qx, int qy, int qz, Climate.Sampler sampler,
                                                  CallbackInfoReturnable<Holder<Biome>> ci) {
        Holder<Biome> chosen=CoastalBiomeIntegration.intercept((MultiNoiseBiomeSource) (Object) this,sampler,qx,qy,qz);
        if(chosen!=null)ci.setReturnValue(chosen);
    }
}

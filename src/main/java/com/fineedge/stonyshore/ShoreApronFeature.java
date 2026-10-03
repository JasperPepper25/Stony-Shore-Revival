package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import com.fineedge.stonyshore.generation.ShoreSurfacePass;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Ocean-side material continuation; never edits ocean density or places legacy geometry. */
public final class ShoreApronFeature extends Feature<NoneFeatureConfiguration> {
    public ShoreApronFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        return ShoreSurfacePass.apply(ctx.level(),new ChunkPos(ctx.origin()),true);
    }
}

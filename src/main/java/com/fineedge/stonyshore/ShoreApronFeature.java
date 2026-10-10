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
        boolean recording=com.fineedge.stonyshore.audit.GenerationRecording.active(ctx.level().getLevel());
        long started=recording?System.nanoTime():0;
        try { return ShoreSurfacePass.apply(ctx.level(),new ChunkPos(ctx.origin()),true); }
        finally {
            if(recording)com.fineedge.stonyshore.audit.GenerationRecording.feature(ctx.level().getLevel(),new ChunkPos(ctx.origin()),System.nanoTime()-started);
        }
    }
}

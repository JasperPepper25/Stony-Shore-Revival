package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import com.fineedge.stonyshore.generation.ShoreSurfacePass;
import com.fineedge.stonyshore.generation.ShoreDecorationPass;
import com.fineedge.stonyshore.generation.CoastalCaveDecorationPass;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Coastal material and decoration continuation, finishing caves once across the full chunk. */
public final class ShoreApronFeature extends Feature<NoneFeatureConfiguration> {
    public ShoreApronFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        boolean recording=com.fineedge.stonyshore.audit.GenerationRecording.active(ctx.level().getLevel());
        long started=recording?System.nanoTime():0;
        try {
            ChunkPos chunk=new ChunkPos(ctx.origin());
            boolean changed=ShoreSurfacePass.apply(ctx.level(),chunk,true);
            changed|=ShoreDecorationPass.apply(ctx.level(),chunk,true);
            changed|=CoastalCaveDecorationPass.apply(ctx.level(),chunk);
            return changed;
        }
        finally {
            if(recording)com.fineedge.stonyshore.audit.GenerationRecording.feature(ctx.level().getLevel(),new ChunkPos(ctx.origin()),System.nanoTime()-started);
        }
    }
}

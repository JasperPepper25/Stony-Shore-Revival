package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import com.fineedge.stonyshore.generation.LegacyPoolPass;
import com.fineedge.stonyshore.generation.ShoreSpirePass;
import com.fineedge.stonyshore.generation.ShoreSurfacePass;
import com.fineedge.stonyshore.generation.ShoreDecorationPass;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Surface finishing; old geometry is suspended while the noise-stage prototype is installed. */
public final class ShoreDetailFeature extends Feature<NoneFeatureConfiguration> {
    public ShoreDetailFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        boolean recording=com.fineedge.stonyshore.audit.GenerationRecording.active(ctx.level().getLevel());
        long started=recording?System.nanoTime():0;
        try {
            ChunkPos chunk = new ChunkPos(ctx.origin());
            boolean changed = ShoreSurfacePass.apply(ctx.level(), chunk);
            changed |= ShoreDecorationPass.apply(ctx.level(), chunk);
            if (!CoastalTerrainIntegration.installed(ctx.level().getLevel())) {
                changed |= LegacyPoolPass.apply(ctx.level(), chunk);
                changed |= ShoreSpirePass.apply(ctx.level(), chunk);
            }
            return changed;
        } finally {
            if(recording)com.fineedge.stonyshore.audit.GenerationRecording.feature(ctx.level().getLevel(),new ChunkPos(ctx.origin()),System.nanoTime()-started);
        }

    }
}

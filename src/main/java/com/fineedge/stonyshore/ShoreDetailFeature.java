package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import com.fineedge.stonyshore.generation.LegacyPoolPass;
import com.fineedge.stonyshore.generation.ShoreSpirePass;
import com.fineedge.stonyshore.generation.ShoreSurfacePass;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** V4 compatibility entry point during the terrain audit. New terrain is not wired here yet. */
public final class ShoreDetailFeature extends Feature<NoneFeatureConfiguration> {
    public ShoreDetailFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        ChunkPos chunk = new ChunkPos(ctx.origin());
        boolean changed = ShoreSurfacePass.apply(ctx.level(), chunk);
        changed |= LegacyPoolPass.apply(ctx.level(), chunk);
        changed |= ShoreSpirePass.apply(ctx.level(), chunk);
        return changed;
    }
}

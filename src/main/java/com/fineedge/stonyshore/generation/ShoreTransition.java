package com.fineedge.stonyshore.generation;

import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Samples neighboring biome rules without requesting chunks or reading mutable decorations. */
final class ShoreTransition {
    private ShoreTransition() {}
    static BlockState inlandCap(ServerLevel level,int x,int y,int z) {
        var generator=level.getChunkSource().getGenerator();var climate=level.getChunkSource().randomState().sampler();
        for(int radius=8;radius<=32;radius+=8)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            if(dx==0 && dz==0)continue;
            var biome=generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x+dx*radius),QuartPos.fromBlock(y),
                QuartPos.fromBlock(z+dz*radius),climate);
            if(biome.is(Biomes.STONY_SHORE) || biome.is(BiomeTags.IS_OCEAN))continue;
            if(biome.is(BiomeTags.IS_BADLANDS))return Blocks.TERRACOTTA.defaultBlockState();
            if(biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BEACH) || biome.value().getBaseTemperature()>1.5F)
                return Blocks.SAND.defaultBlockState();
            if(biome.is(BiomeTags.IS_MOUNTAIN))return Blocks.STONE.defaultBlockState();
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }
    static double rockWeight(double inlandDistance) {
        double v=Math.max(0,Math.min(1,inlandDistance/24));return v*v*(3-2*v);
    }
}

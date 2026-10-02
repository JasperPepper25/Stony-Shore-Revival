package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;


public final class ShoreBlocks {
    private ShoreBlocks() {}
    static boolean isSourceStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.CALCITE) || state.is(Blocks.GRANITE);
    }

    static boolean isRock(BlockState state) {
        if (isSourceStone(state) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
            || state.is(Blocks.TUFF) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PACKED_ICE)) return true;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        return id.toString().equals("biomeswevegone:overgrown_stone")
            || id.toString().equals("biomeswevegone:mossy_stone")
            || id.toString().equals("biomeswevegone:rocky_stone")
            || id.toString().equals("regions_unexplored:verdant_stone")
            || id.toString().equals("biomeswevegone:verdant_stone")
            || id.toString().equals("hybrid_aquatic:verdant_stone");
    }

    static boolean hasOpenSide(WorldGenLevel world, BlockPos pos) {
        return world.getBlockState(pos.north()).isAir() || world.getBlockState(pos.south()).isAir()
            || world.getBlockState(pos.east()).isAir() || world.getBlockState(pos.west()).isAir()
            || !world.getFluidState(pos.north()).isEmpty() || !world.getFluidState(pos.south()).isEmpty()
            || !world.getFluidState(pos.east()).isEmpty() || !world.getFluidState(pos.west()).isEmpty();
    }

    static Block optionalBlock(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.BLOCKS.containsKey(key)) return null;
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        return block == Blocks.AIR ? null : block;
    }

    static Block firstAvailable(String... ids) {
        for (String id : ids) {
            Block block = optionalBlock(id);
            if (block != null) return block;
        }
        return null;
    }

    static List<Block> optionalBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (String id : ShoreConfig.EXTRA_ROCKS.get()) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key != null && ForgeRegistries.BLOCKS.containsKey(key)) {
                Block block = ForgeRegistries.BLOCKS.getValue(key);
                if (block != null && block != Blocks.AIR) blocks.add(block);
            }
        }
        return blocks;
    }

    static boolean isCold(WorldGenLevel world, BlockPos pos) {
        if (world.getBiome(pos).value().getBaseTemperature() <= 0.15F) return true;
        for (int dx = -12; dx <= 12; dx += 12) {
            for (int dz = -12; dz <= 12; dz += 12) {
                if (dx == 0 && dz == 0) continue;
                if (world.getBiome(pos.offset(dx, 0, dz)).value().getBaseTemperature() <= 0.15F)
                    return true;
            }
        }
        return false;
    }

    static boolean nearOcean(WorldGenLevel world, BlockPos pos) {
        for (int d : new int[]{4, 8, 12}) {
            if (world.getBiome(pos.offset(d, 0, 0)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(-d, 0, 0)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(0, 0, d)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(0, 0, -d)).is(BiomeTags.IS_OCEAN)) return true;
        }
        return false;
    }

}

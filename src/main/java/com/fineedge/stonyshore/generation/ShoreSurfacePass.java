package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;


public final class ShoreSurfacePass {
    private ShoreSurfacePass() {}
    public static boolean apply(WorldGenLevel world, ChunkPos chunk) {
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        int sea = world.getSeaLevel();
        List<Block> extras = optionalBlocks();
        Block overgrown = optionalBlock("biomeswevegone:overgrown_stone");
        Block verdant = firstAvailable("regions_unexplored:verdant_stone",
            "biomeswevegone:verdant_stone", "hybrid_aquatic:verdant_stone",
            "biomeswevegone:mossy_stone");
        Block rocky = optionalBlock("biomeswevegone:rocky_stone");
        boolean any = false;
        boolean coastalTerrain = CoastalTerrainIntegration.installed(world.getLevel());

        // World-coordinate value noise makes adjacent chunks agree on the same broad bands.
        for (int x = minX; x < minX + 16; ++x) {
            for (int z = minZ; z < minZ + 16; ++z) {
                int top = world.getHeight(coastalTerrain ? Heightmap.Types.OCEAN_FLOOR_WG
                    : Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top < sea - 10) continue;
                BlockPos surface = new BlockPos(x, top, z);
                if (!world.getBiome(surface).is(Biomes.STONY_SHORE)) continue;
                boolean cold = ShoreConfig.COLD.get() && isCold(world, surface);
                boolean coast = !coastalTerrain && top >= sea && top <= sea + 8
                    && world.getBlockState(surface.above()).isAir() && nearOcean(world, surface);
                if (coastalTerrain && top >= sea - 1 && top <= sea + 3) {
                    var column = CoastalTerrainIntegration.column(world.getLevel(), x, z);
                    if (column != null && column.sandStrength() > 0.6) {
                        BlockState above = world.getBlockState(surface.above());
                        // Supported, two-block sand caps with a sandstone base. Never cover an
                        // ore, vegetation, structure block or an unsupported cave roof with sand.
                        if ((above.isAir() || above.is(Blocks.WATER))
                            && isSourceStone(world.getBlockState(surface))
                            && isSourceStone(world.getBlockState(surface.below()))
                            && isSourceStone(world.getBlockState(surface.below(2)))
                            && isSourceStone(world.getBlockState(surface.below(3)))) {
                            world.setBlock(surface.below(2), Blocks.SANDSTONE.defaultBlockState(), 2);
                            world.setBlock(surface.below(), Blocks.SAND.defaultBlockState(), 2);
                            world.setBlock(surface, Blocks.SAND.defaultBlockState(), 2);
                            any = true;
                        }
                    }
                }
                double band = valueNoise(x, z, 42);
                double damp = valueNoise(x + 913, z - 457, 26);
                double cove = valueNoise(x - 1781, z + 654, 48);
                double tuff = valueNoise(x + 2764, z - 3852, 11);
                // Include visible cliff faces, but never excavate a cliff or replace ores.
                for (int y = top; y >= Math.max(sea - 10, top - 92); --y) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState old = world.getBlockState(pos);
                    if (!isSourceStone(old)) continue;
                    if (y != top && !hasOpenSide(world, pos)) continue;
                    double grain = unit(hash(x * 11L, y * 23L, z * 11L));
                    BlockState next = palette(old, y, top, sea, band, damp, cove, tuff, grain,
                        cold, coast, overgrown, verdant, rocky, extras);
                    if (next != old && !next.equals(old)) {
                        world.setBlock(pos, next, 2);
                        any = true;
                    }
                }
                if (cold && isSourceStone(world.getBlockState(surface))
                    && world.getBlockState(surface.above()).isAir()
                    && unit(hash(x, top, z)) < (top <= sea + 8 ? 0.36 : 0.19)
                    && Blocks.SNOW.defaultBlockState().canSurvive(world, surface.above())) {
                    world.setBlock(surface.above(), Blocks.SNOW.defaultBlockState(), 2);
                    any = true;
                }
            }
        }

        return any;
    }

    private static BlockState palette(BlockState old, int y, int top, int sea, double band,
                                      double damp, double cove, double tuff, double grain, boolean cold, boolean coast,
                                      Block overgrown, Block verdant, Block rocky, List<Block> extras) {
        // Beach caps can replace natural calcite and granite too; the broad band is decided
        // before the ordinary rock palette and is never thinned by per-block dithering.
        if (coast && y == top && cove > 0.38)
            return grain < 0.08 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        if (old.is(Blocks.CALCITE) || old.is(Blocks.GRANITE)) {
            if (grain > 0.13) return old; // Keep naturally generated light and warm strata.
            return grain < 0.045 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
        // A smaller-scale field makes connected tuff ripples on exposed stone shelves.
        // Leave wetter moss patches and the natural calcite/granite strata intact.
        if (y == top && old.is(Blocks.STONE) && damp <= 0.56 && band < 0.44
            && tuff > 0.56 && grain < 0.82) return Blocks.TUFF.defaultBlockState();
        if (!old.is(Blocks.STONE) && grain > 0.28) return old;
        if (grain > (damp > 0.56 && y == top ? Math.max(0.70, ShoreConfig.STONE_CHANCE.get())
            : ShoreConfig.STONE_CHANCE.get())) return old;
        boolean tidal = y <= sea + 9;
        if (cold && tidal && damp > 0.74 && grain < 0.035) return Blocks.PACKED_ICE.defaultBlockState();
        if (!cold && damp > 0.56 && y >= sea - 2 && (y == top || tidal)) {
            if (y == top && grain < 0.14) return Blocks.MOSS_BLOCK.defaultBlockState();
            if (overgrown != null && y == top && grain < 0.29) return overgrown.defaultBlockState();
            if (verdant != null && grain < 0.40) return verdant.defaultBlockState();
            if (grain < 0.53) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (rocky != null && band > 0.69 && grain < 0.12) return rocky.defaultBlockState();
        if (!extras.isEmpty() && grain > 0.33 && band > 0.63)
            return extras.get(Math.min(extras.size() - 1, (int) (grain * extras.size()))).defaultBlockState();
        if (band < 0.24) return grain < 0.16 ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        if (band > 0.76) return grain < 0.19 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        return grain < 0.16 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
    }

}

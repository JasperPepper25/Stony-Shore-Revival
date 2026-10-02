package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;



public final class ShoreSpirePass {
    private ShoreSpirePass() {}
    public static boolean apply(WorldGenLevel world, ChunkPos chunk) {
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        int sea = world.getSeaLevel();
        boolean any = false;
        if (ShoreConfig.SPIRES.get() && unit(hash(minX, 73, minZ)) < ShoreConfig.SPIRE_CHANCE.get()) {
            int anchorX = -1, anchorZ = -1, placed = 0;
            int[] placedX = new int[3], placedZ = new int[3];
            for (int i = 0; i < 24 && placed < 3; i++) {
                long sample = hash(minX, i + 97, minZ);
                int x = minX + 4 + (int) Math.floorMod(sample, 8L);
                int z = minZ + 4 + (int) Math.floorMod(sample >>> 16, 8L);
                int anchorDistance = (x - anchorX) * (x - anchorX) + (z - anchorZ) * (z - anchorZ);
                if (placed > 0 && (anchorDistance < 36 || anchorDistance > 100)) continue;
                boolean overlaps = false;
                for (int j = 0; j < placed; j++)
                    if ((x - placedX[j]) * (x - placedX[j]) + (z - placedZ[j]) * (z - placedZ[j]) < 36)
                        overlaps = true;
                if (overlaps) continue;
                if (makeSpire(world, x, z, sea, sample)) {
                    if (placed == 0) { anchorX = x; anchorZ = z; }
                    placedX[placed] = x; placedZ[placed++] = z;
                    any = true;
                }
            }
        }
        return any;
    }

    private static boolean makeSpire(WorldGenLevel world, int x, int z, int sea, long choice) {
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockPos base = new BlockPos(x, y, z);
        if (y < sea + 1 || y > sea + 96 || !world.getBiome(base).is(Biomes.STONY_SHORE)
            || !isRock(world.getBlockState(base))) return false;
        int style = (int) Math.floorMod(choice >>> 36, 3L);
        int height = style == 0 ? 9 + (int) Math.floorMod(choice >>> 42, 5L)
            : style == 1 ? 7 + (int) Math.floorMod(choice >>> 42, 5L)
            : 6 + (int) Math.floorMod(choice >>> 42, 4L);
        double baseRadius = style == 1 ? 2.8 : style == 0 ? 2.15 : 2.45;
        int leanX = (int) Math.floorMod(choice >>> 48, 3L) - 1;
        int leanZ = (int) Math.floorMod(choice >>> 52, 3L) - 1;
        // Validate all planned blocks before placing anything; confine the whole cluster
        // to its origin chunk and keep existing terrain, plants, and structures intact.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean foundation = spireCell(dx, dz, 1, height, baseRadius, 0, 0, choice);
            boolean body = false;
            for (int layer = 2; layer <= height; layer++)
                body |= spireCell(dx, dz, layer, height, baseRadius, leanX, leanZ, choice);
            if (!foundation && !body) continue;
            int px = x + dx, pz = z + dz;
            int groundY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
            BlockPos ground = new BlockPos(px, groundY, pz);
            if (groundY < y - 2 || groundY > y + 2 || !world.getBiome(ground).is(Biomes.STONY_SHORE)
                || !isRock(world.getBlockState(ground))) return false;
            for (int fillY = groundY + 1; foundation && fillY <= y; fillY++)
                if (!world.getBlockState(new BlockPos(px, fillY, pz)).isAir()) return false;
            for (int layer = 1; layer <= height; layer++) {
                if (!spireCell(dx, dz, layer, height, baseRadius, leanX, leanZ, choice)) continue;
                if (y + layer <= groundY) return false;
                if (!world.getBlockState(new BlockPos(px, y + layer, pz)).isAir()) return false;
            }
        }
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            boolean foundation = spireCell(dx, dz, 1, height, baseRadius, 0, 0, choice);
            int px = x + dx, pz = z + dz;
            if (foundation) {
                int groundY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
                for (int fillY = groundY + 1; fillY <= y; fillY++)
                    world.setBlock(new BlockPos(px, fillY, pz), Blocks.STONE.defaultBlockState(), 2);
            }
            for (int layer = 1; layer <= height; layer++) {
                if (!spireCell(dx, dz, layer, height, baseRadius, leanX, leanZ, choice)) continue;
                BlockPos p = new BlockPos(px, y + layer, pz);
                double radius = spireRadius(layer, height, baseRadius);
                int offsetX = Math.round(leanX * (layer - 1f) / (height - 1));
                int offsetZ = Math.round(leanZ * (layer - 1f) / (height - 1));
                double distance = Math.hypot(dx - offsetX, dz - offsetZ);
                BlockState block;
                if (layer == height) block = Blocks.COBBLESTONE_SLAB.defaultBlockState();
                else if (distance <= radius - 0.55 || layer == 1) block = (layer % 3 == 0
                    ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
                else if (Math.floorMod(hash(px, layer, pz), 4L) == 0)
                    block = Blocks.ANDESITE_SLAB.defaultBlockState();
                else {
                    Direction facing = Math.abs(dx - offsetX) >= Math.abs(dz - offsetZ)
                        ? (dx > offsetX ? Direction.WEST : Direction.EAST)
                        : (dz > offsetZ ? Direction.NORTH : Direction.SOUTH);
                    block = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
                }
                world.setBlock(p, block, 2);
            }
        }
        return true;
    }

    private static double spireRadius(int layer, int height, double baseRadius) {
        return baseRadius * Math.pow(1.0 - (layer - 1.0) / height, 0.8);
    }

    private static boolean spireCell(int dx, int dz, int layer, int height, double baseRadius,
                                     int leanX, int leanZ, long choice) {
        int offsetX = Math.round(leanX * (layer - 1f) / (height - 1));
        int offsetZ = Math.round(leanZ * (layer - 1f) / (height - 1));
        if (layer == height) return dx == offsetX && dz == offsetZ;
        double angle = Math.atan2(dz - offsetZ, dx - offsetX);
        double roughness = 0.17 * Math.sin(angle * 3 + unit(choice) * 6.28 + layer * 0.28);
        return Math.hypot(dx - offsetX, dz - offsetZ) <= spireRadius(layer, height, baseRadius) + roughness;
    }

}

package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;



public final class LegacyPoolPass {
    private LegacyPoolPass() {}
    public static boolean apply(WorldGenLevel world, ChunkPos chunk) {
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        int sea = world.getSeaLevel();
        boolean any = false;
        long choice = hash(minX >> 4, 0, minZ >> 4);
        // Give larger basins the first attempts. The complete footprint stays within this chunk.
        if (ShoreConfig.POOLS.get() && unit(choice) < ShoreConfig.POOL_CHANCE.get()) {
            int placed = 0;
            for (int i = 0; i < 28 && placed < 2; i++) {
                long sample = hash(minX, i + 31, minZ);
                int radius = i < 12 ? 4 + i % 3 : 2 + i % 4;
                int margin = radius + 1;
                int span = 16 - 2 * margin;
                int depth = 1 + (int) Math.floorMod(sample >>> 42, 3L);
                if (makePool(world, minX + margin + (int) Math.floorMod(sample, span),
                    minZ + margin + (int) Math.floorMod(sample >>> 16, span), sea, radius, depth, sample)) {
                    any = true;
                    placed++;
                }
            }
        }
        return any;
    }

    private static boolean makePool(WorldGenLevel world, int cx, int cz, int sea, int radius, int depth, long choice) {
        int waterline = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1;
        if (waterline < sea || waterline > sea + 64) return false;
        BlockPos center = new BlockPos(cx, waterline, cz);
        if (!world.getBiome(center).is(Biomes.STONY_SHORE)) return false;
        int outer = radius + 1;
        int size = 2 * outer + 1;
        boolean[][] basin = new boolean[size][size];
        boolean[][] rim = new boolean[size][size];
        double xStretch = 0.77 + 0.23 * unit(hash(choice, 1, 7));
        double zStretch = 0.77 + 0.23 * unit(hash(choice, 2, 7));
        int noiseX = (int) Math.floorMod(choice, 100000L);
        int noiseZ = (int) Math.floorMod(choice >>> 24, 100000L);
        int cells = 0;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            double distance = Math.hypot(dx / xStretch, dz / zStretch);
            double wobble = (valueNoise(cx + dx + noiseX, cz + dz + noiseZ, 3) - 0.5) * 1.8;
            if (distance <= radius - 0.15 + wobble) {
                basin[dx + outer][dz + outer] = true;
                cells++;
            }
        }
        if (cells < 5 || !basin[outer][outer]) return false;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (!basin[dx + outer][dz + outer]) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int rx = dx + dir.getStepX() + outer, rz = dz + dir.getStepZ() + outer;
                if (!basin[rx][rz]) rim[rx][rz] = true;
            }
        }

        // First prove the entire basin and its enclosing lip have solid, natural rock.
        // A two-block lip can bridge a small downhill step; reject caves, water, plants,
        // and foreign features rather than cutting through them.
        for (int dx = -outer; dx <= outer; dx++) for (int dz = -outer; dz <= outer; dz++) {
            boolean wet = basin[dx + outer][dz + outer];
            if (!wet && !rim[dx + outer][dz + outer]) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos ground = new BlockPos(x, h, z);
            int floor = wet ? poolFloor(waterline, depth, dx, dz, radius, true) : h;
            if (!world.getBiome(ground).is(Biomes.STONY_SHORE)
                || h < waterline - 2 || h > waterline + 4
                || !isRock(world.getBlockState(ground))
                || !world.getBlockState(ground.above()).isAir()
                || h < floor
                || !isRock(world.getBlockState(new BlockPos(x, floor - 1, z)))
                || !isRock(world.getBlockState(new BlockPos(x, floor - 2, z)))) return false;
            for (int y = floor; y <= h; y++)
                if (!isRock(world.getBlockState(new BlockPos(x, y, z)))) return false;
            if (!wet) for (int y = h + 1; y <= waterline; y++)
                if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
        }
        for (int dx = -outer; dx <= outer; dx++) for (int dz = -outer; dz <= outer; dz++) {
            boolean wet = basin[dx + outer][dz + outer];
            if (!wet && !rim[dx + outer][dz + outer]) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (!wet) {
                for (int y = h + 1; y <= waterline; y++)
                    world.setBlock(new BlockPos(x, y, z),
                        (Math.floorMod(hash(x, y, z), 5L) == 0 ? Blocks.COBBLESTONE : Blocks.STONE)
                            .defaultBlockState(), 2);
                continue;
            }
            int floor = poolFloor(waterline, depth, dx, dz, radius, true);
            for (int y = h; y > waterline; y--)
                world.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            for (int y = floor + 1; y <= waterline; y++)
                world.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 2);
            BlockPos aquatic = new BlockPos(x, floor + 1, z);
            double r = unit(hash(x, floor, z));
            if (r < 0.06 && Blocks.SEA_PICKLE.defaultBlockState().canSurvive(world, aquatic))
                world.setBlock(aquatic, Blocks.SEA_PICKLE.defaultBlockState(), 2);
            else if (r < 0.16 && Blocks.SEAGRASS.defaultBlockState().canSurvive(world, aquatic))
                world.setBlock(aquatic, Blocks.SEAGRASS.defaultBlockState(), 2);
        }
        return true;
    }

    private static int poolFloor(int waterline, int depth, int dx, int dz, int radius, boolean wet) {
        if (!wet) return waterline;
        // Deeper center, shallow irregular margins, all beneath a level water surface.
        int shelf = dx * dx + dz * dz > (radius - 1) * (radius - 1) && depth > 1 ? 1 : 0;
        return waterline - depth + shelf;
    }

}

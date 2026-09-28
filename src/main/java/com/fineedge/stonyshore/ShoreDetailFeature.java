package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** A late, bounded decoration pass. No biome source, noise settings, or existing features are replaced. */
public final class ShoreDetailFeature extends Feature<NoneFeatureConfiguration> {
    public ShoreDetailFeature(Codec<NoneFeatureConfiguration> codec) { super(codec); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel world = ctx.level();
        ChunkPos chunk = new ChunkPos(ctx.origin());
        int minX = chunk.getMinBlockX(), minZ = chunk.getMinBlockZ();
        int sea = world.getSeaLevel();
        List<Block> extras = optionalBlocks();
        boolean any = false;

        // World-coordinate value noise makes adjacent chunks agree on the same broad bands.
        for (int x = minX; x < minX + 16; ++x) {
            for (int z = minZ; z < minZ + 16; ++z) {
                int top = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top < sea - 10) continue;
                BlockPos surface = new BlockPos(x, top, z);
                if (!world.getBiome(surface).is(Biomes.STONY_SHORE)) continue;
                boolean cold = ShoreConfig.COLD.get() && isCold(world, surface);
                boolean coast = top <= sea + 5 && nearOcean(world, surface);
                double band = valueNoise(x, z, 42);
                double damp = valueNoise(x + 913, z - 457, 28);
                // Include visible cliff faces, but never excavate a cliff or replace ores.
                for (int y = top; y >= Math.max(sea - 10, top - 92); --y) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState old = world.getBlockState(pos);
                    if (!isSourceStone(old)) continue;
                    if (y != top && !hasOpenSide(world, pos)) continue;
                    double grain = unit(hash(x * 11L, y * 23L, z * 11L));
                    BlockState next = palette(old, y, sea, band, damp, grain, cold, coast, extras);
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

        long choice = hash(minX >> 4, 0, minZ >> 4);
        if (ShoreConfig.POOLS.get() && Math.floorMod(choice, 7L) == 0L)
            any |= makePool(world, minX + 4 + (int) Math.floorMod(choice >>> 8, 8L),
                minZ + 4 + (int) Math.floorMod(choice >>> 16, 8L), sea);
        if (ShoreConfig.SPIRES.get() && Math.floorMod(choice, 11L) == 1L)
            any |= makeSpire(world, minX + 5 + (int) Math.floorMod(choice >>> 24, 6L),
                minZ + 5 + (int) Math.floorMod(choice >>> 32, 6L), sea, choice);
        return any;
    }

    private static boolean isSourceStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.CALCITE) || state.is(Blocks.GRANITE);
    }

    private static boolean isRock(BlockState state) {
        return isSourceStone(state) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
            || state.is(Blocks.TUFF) || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.PACKED_ICE);
    }

    private static boolean hasOpenSide(WorldGenLevel world, BlockPos pos) {
        return world.getBlockState(pos.north()).isAir() || world.getBlockState(pos.south()).isAir()
            || world.getBlockState(pos.east()).isAir() || world.getBlockState(pos.west()).isAir()
            || !world.getFluidState(pos.north()).isEmpty() || !world.getFluidState(pos.south()).isEmpty()
            || !world.getFluidState(pos.east()).isEmpty() || !world.getFluidState(pos.west()).isEmpty();
    }

    private static BlockState palette(BlockState old, int y, int sea, double band,
                                      double damp, double grain, boolean cold, boolean coast, List<Block> extras) {
        if (old.is(Blocks.CALCITE) || old.is(Blocks.GRANITE)) {
            if (grain > 0.13) return old; // Keep naturally generated light and warm strata.
            return grain < 0.045 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
        if (!old.is(Blocks.STONE) && grain > 0.28) return old;
        if (grain > ShoreConfig.STONE_CHANCE.get()) return old;
        boolean tidal = y <= sea + 9;
        if (coast && y >= sea && y <= sea + 3 && band > 0.78 && grain < 0.30)
            return grain < 0.08 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        if (cold && tidal && damp > 0.74 && grain < 0.035) return Blocks.PACKED_ICE.defaultBlockState();
        if (!cold && tidal && damp > 0.63 && grain < 0.075) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        if (!cold && tidal && damp > 0.80 && grain < 0.055) return Blocks.MOSS_BLOCK.defaultBlockState();
        if (!extras.isEmpty() && grain > 0.33 && band > 0.63)
            return extras.get(Math.min(extras.size() - 1, (int) (grain * extras.size()))).defaultBlockState();
        if (band < 0.24) return grain < 0.16 ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        if (band > 0.76) return grain < 0.19 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        return grain < 0.16 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
    }

    private static List<Block> optionalBlocks() {
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

    private static boolean isCold(WorldGenLevel world, BlockPos pos) {
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

    private static boolean nearOcean(WorldGenLevel world, BlockPos pos) {
        return world.getBiome(pos.offset(8, 0, 0)).is(BiomeTags.IS_OCEAN)
            || world.getBiome(pos.offset(-8, 0, 0)).is(BiomeTags.IS_OCEAN)
            || world.getBiome(pos.offset(0, 0, 8)).is(BiomeTags.IS_OCEAN)
            || world.getBiome(pos.offset(0, 0, -8)).is(BiomeTags.IS_OCEAN);
    }

    private static boolean makePool(WorldGenLevel world, int cx, int cz, int sea) {
        int top = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1;
        if (top < sea + 2 || top > sea + 8) return false;
        BlockPos center = new BlockPos(cx, top, cz);
        if (!world.getBiome(center).is(Biomes.STONY_SHORE)) return false;
        // A one-block-deep basin, contained by existing rim rock; never open a drain into a cave.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > 16) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos p = new BlockPos(x, h, z);
            if (!world.getBiome(p).is(Biomes.STONY_SHORE) || h < top || h > top + 1
                || !isRock(world.getBlockState(p)) || !isRock(world.getBlockState(new BlockPos(x, top - 2, z)))) return false;
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            for (int y = h; y >= top - 1; --y)
                world.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 2);
            BlockPos aquatic = new BlockPos(x, top - 1, z);
            double r = unit(hash(x, top, z));
            if (r < 0.06 && Blocks.SEA_PICKLE.defaultBlockState().canSurvive(world, aquatic))
                world.setBlock(aquatic, Blocks.SEA_PICKLE.defaultBlockState(), 2);
            else if (r < 0.17 && Blocks.SEAGRASS.defaultBlockState().canSurvive(world, aquatic))
                world.setBlock(aquatic, Blocks.SEAGRASS.defaultBlockState(), 2);
        }
        return true;
    }

    private static boolean makeSpire(WorldGenLevel world, int x, int z, int sea, long choice) {
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockPos base = new BlockPos(x, y, z);
        if (y < sea + 1 || y > sea + 28 || !world.getBiome(base).is(Biomes.STONY_SHORE)
            || !isRock(world.getBlockState(base))) return false;
        int height = 3 + (int) Math.floorMod(choice >>> 42, 4L);
        for (int i = 1; i <= height; i++)
            if (!world.getBlockState(base.above(i)).isAir()) return false;
        for (int i = 1; i <= height; i++) {
            BlockState stone = i == height ? Blocks.COBBLESTONE.defaultBlockState()
                : (i % 3 == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
            world.setBlock(base.above(i), stone, 2);
        }
        return true;
    }

    private static double valueNoise(int x, int z, int scale) {
        int gx = Math.floorDiv(x, scale), gz = Math.floorDiv(z, scale);
        double fx = (x - gx * scale) / (double) scale, fz = (z - gz * scale) / (double) scale;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = unit(hash(gx, 0, gz)), b = unit(hash(gx + 1, 0, gz));
        double c = unit(hash(gx, 0, gz + 1)), d = unit(hash(gx + 1, 0, gz + 1));
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    private static long hash(long x, long y, long z) {
        long h = x * 0x632BE59BD9B4E019L ^ y * 0x9E3779B97F4A7C15L ^ z * 0xC6BC279692B5CC83L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double unit(long value) { return (value >>> 11) * 0x1.0p-53; }
}

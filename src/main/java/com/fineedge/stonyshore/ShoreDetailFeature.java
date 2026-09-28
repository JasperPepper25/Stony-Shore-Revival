package com.fineedge.stonyshore;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
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
        Block overgrown = optionalBlock("biomeswevegone:overgrown_stone");
        Block verdant = firstAvailable("regions_unexplored:verdant_stone",
            "biomeswevegone:verdant_stone", "hybrid_aquatic:verdant_stone",
            "biomeswevegone:mossy_stone");
        Block rocky = optionalBlock("biomeswevegone:rocky_stone");
        boolean any = false;

        // World-coordinate value noise makes adjacent chunks agree on the same broad bands.
        for (int x = minX; x < minX + 16; ++x) {
            for (int z = minZ; z < minZ + 16; ++z) {
                int top = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top < sea - 10) continue;
                BlockPos surface = new BlockPos(x, top, z);
                if (!world.getBiome(surface).is(Biomes.STONY_SHORE)) continue;
                boolean cold = ShoreConfig.COLD.get() && isCold(world, surface);
                boolean coast = top >= sea && top <= sea + 8
                    && world.getBlockState(surface.above()).isAir() && nearOcean(world, surface);
                double band = valueNoise(x, z, 42);
                double damp = valueNoise(x + 913, z - 457, 26);
                double cove = valueNoise(x - 1781, z + 654, 48);
                // Include visible cliff faces, but never excavate a cliff or replace ores.
                for (int y = top; y >= Math.max(sea - 10, top - 92); --y) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState old = world.getBlockState(pos);
                    if (!isSourceStone(old)) continue;
                    if (y != top && !hasOpenSide(world, pos)) continue;
                    double grain = unit(hash(x * 11L, y * 23L, z * 11L));
                    BlockState next = palette(old, y, top, sea, band, damp, cove, grain,
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

        long choice = hash(minX >> 4, 0, minZ >> 4);
        // Try several independent locations: one unlucky center should not suppress a whole chunk.
        if (ShoreConfig.POOLS.get() && unit(choice) < ShoreConfig.POOL_CHANCE.get()) {
            int placed = 0;
            for (int i = 0; i < 20 && placed < 2; i++) {
                long sample = hash(minX, i + 31, minZ);
                int radius = 1 + (int) Math.floorMod(sample >>> 36, 3L);
                int depth = 1 + (int) Math.floorMod(sample >>> 42, 2L);
                if (makePool(world, minX + 4 + (int) Math.floorMod(sample, 8L),
                    minZ + 4 + (int) Math.floorMod(sample >>> 16, 8L), sea, radius, depth)) {
                    any = true;
                    placed++;
                }
            }
        }
        if (ShoreConfig.SPIRES.get() && unit(hash(minX, 73, minZ)) < ShoreConfig.SPIRE_CHANCE.get()) {
            for (int i = 0; i < 6; i++) {
                long sample = hash(minX, i + 97, minZ);
                if (makeSpire(world, minX + 3 + (int) Math.floorMod(sample, 10L),
                    minZ + 3 + (int) Math.floorMod(sample >>> 16, 10L), sea, sample)) { any = true; break; }
            }
        }
        return any;
    }

    private static boolean isSourceStone(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.ANDESITE)
            || state.is(Blocks.CALCITE) || state.is(Blocks.GRANITE);
    }

    private static boolean isRock(BlockState state) {
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

    private static boolean hasOpenSide(WorldGenLevel world, BlockPos pos) {
        return world.getBlockState(pos.north()).isAir() || world.getBlockState(pos.south()).isAir()
            || world.getBlockState(pos.east()).isAir() || world.getBlockState(pos.west()).isAir()
            || !world.getFluidState(pos.north()).isEmpty() || !world.getFluidState(pos.south()).isEmpty()
            || !world.getFluidState(pos.east()).isEmpty() || !world.getFluidState(pos.west()).isEmpty();
    }

    private static BlockState palette(BlockState old, int y, int top, int sea, double band,
                                      double damp, double cove, double grain, boolean cold, boolean coast,
                                      Block overgrown, Block verdant, Block rocky, List<Block> extras) {
        // Beach caps can replace natural calcite and granite too; the broad band is decided
        // before the ordinary rock palette and is never thinned by per-block dithering.
        if (coast && y == top && cove > 0.38)
            return grain < 0.08 ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        if (old.is(Blocks.CALCITE) || old.is(Blocks.GRANITE)) {
            if (grain > 0.13) return old; // Keep naturally generated light and warm strata.
            return grain < 0.045 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
        }
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

    private static Block optionalBlock(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !ForgeRegistries.BLOCKS.containsKey(key)) return null;
        Block block = ForgeRegistries.BLOCKS.getValue(key);
        return block == Blocks.AIR ? null : block;
    }

    private static Block firstAvailable(String... ids) {
        for (String id : ids) {
            Block block = optionalBlock(id);
            if (block != null) return block;
        }
        return null;
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
        for (int d : new int[]{4, 8, 12}) {
            if (world.getBiome(pos.offset(d, 0, 0)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(-d, 0, 0)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(0, 0, d)).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos.offset(0, 0, -d)).is(BiomeTags.IS_OCEAN)) return true;
        }
        return false;
    }

    private static boolean makePool(WorldGenLevel world, int cx, int cz, int sea, int radius, int depth) {
        int waterline = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1;
        if (waterline < sea || waterline > sea + 64) return false;
        int floor = waterline - depth;
        BlockPos center = new BlockPos(cx, waterline, cz);
        if (!world.getBiome(center).is(Biomes.STONY_SHORE)) return false;

        // Validate every basin and rim column before any edit. A low rim can be raised by at
        // most two natural-looking rock blocks; an intact rock floor prevents cave drainage.
        int outer = radius + 1;
        for (int dx = -outer; dx <= outer; dx++) for (int dz = -outer; dz <= outer; dz++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > outer * outer) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            BlockPos ground = new BlockPos(x, h, z);
            if (!world.getBiome(ground).is(Biomes.STONY_SHORE)
                || h < waterline - 2 || h > waterline + 2
                || !isRock(world.getBlockState(ground))
                || !world.getBlockState(ground.above()).isAir()
                || !isRock(world.getBlockState(new BlockPos(x, floor - 1, z)))) return false;
            if (d2 <= radius * radius) {
                if (h < floor) return false;
                for (int y = floor; y <= h; y++)
                    if (!isRock(world.getBlockState(new BlockPos(x, y, z)))) return false;
            } else {
                for (int y = Math.min(h, waterline); y <= h; y++)
                    if (!isRock(world.getBlockState(new BlockPos(x, y, z)))) return false;
            }
        }

        for (int dx = -outer; dx <= outer; dx++) for (int dz = -outer; dz <= outer; dz++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > outer * outer) continue;
            int x = cx + dx, z = cz + dz;
            int h = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (d2 > radius * radius) {
                for (int y = h + 1; y <= waterline; y++)
                    world.setBlock(new BlockPos(x, y, z),
                        (Math.floorMod(hash(x, y, z), 4L) == 0 ? Blocks.COBBLESTONE : Blocks.STONE)
                            .defaultBlockState(), 2);
                continue;
            }
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

    private static boolean makeSpire(WorldGenLevel world, int x, int z, int sea, long choice) {
        int y = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        BlockPos base = new BlockPos(x, y, z);
        if (y < sea + 1 || y > sea + 96 || !world.getBiome(base).is(Biomes.STONY_SHORE)
            || !isRock(world.getBlockState(base))) return false;
        int height = 5 + (int) Math.floorMod(choice >>> 42, 3L);
        // A seven-block-wide rock base narrows each layer. Validate the whole footprint first;
        // never erase foliage, structures, water, or a neighboring biome to make room.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9) continue;
            int px = x + dx, pz = z + dz;
            int groundY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
            BlockPos ground = new BlockPos(px, groundY, pz);
            if (groundY < y - 2 || groundY > y + 2 || !world.getBiome(ground).is(Biomes.STONY_SHORE)
                || !isRock(world.getBlockState(ground))) return false;
            for (int fillY = groundY + 1; fillY <= y; fillY++)
                if (!world.getBlockState(new BlockPos(px, fillY, pz)).isAir()) return false;
            for (int layer = 1; layer <= height; layer++) {
                double radius = 3.0 - (layer - 1) * 2.6 / (height - 1);
                if (dx * dx + dz * dz > radius * radius || y + layer <= groundY) continue;
                if (!world.getBlockState(new BlockPos(px, y + layer, pz)).isAir()) return false;
            }
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (dx * dx + dz * dz > 9) continue;
            int px = x + dx, pz = z + dz;
            int groundY = world.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
            for (int fillY = groundY + 1; fillY <= y; fillY++)
                world.setBlock(new BlockPos(px, fillY, pz), Blocks.STONE.defaultBlockState(), 2);
            for (int layer = 1; layer <= height; layer++) {
                double radius = 3.0 - (layer - 1) * 2.6 / (height - 1);
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius || y + layer <= groundY) continue;
                BlockPos p = new BlockPos(px, y + layer, pz);
                BlockState block;
                if (layer == height) block = Blocks.COBBLESTONE_SLAB.defaultBlockState();
                else if (distance <= radius - 0.75) block = (layer % 3 == 0
                    ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState();
                else if (Math.abs(dx) + Math.abs(dz) == 0 || Math.floorMod(hash(px, layer, pz), 3L) == 0)
                    block = Blocks.ANDESITE_SLAB.defaultBlockState();
                else {
                    Direction facing = Math.abs(dx) >= Math.abs(dz)
                        ? (dx > 0 ? Direction.WEST : Direction.EAST)
                        : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                    block = Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
                }
                world.setBlock(p, block, 2);
            }
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

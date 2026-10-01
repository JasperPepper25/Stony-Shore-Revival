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

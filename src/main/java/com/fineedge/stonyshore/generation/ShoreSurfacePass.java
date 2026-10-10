package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import com.fineedge.stonyshore.terrain.CoastalBiomeIntegration;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
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
    public static boolean apply(WorldGenLevel world, ChunkPos chunk) { return apply(world,chunk,false); }
    public static boolean apply(WorldGenLevel world, ChunkPos chunk, boolean apronOnly) {
        boolean regional=ShoreConfig.LANDFORMS.get() && CoastalTerrainIntegration.installed(world.getLevel());
        if(apronOnly && !regional) return false;
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
        var model=CoastalTerrainIntegration.sampler(world.getLevel());
        var originalSource=world.getLevel().getChunkSource().getGenerator().getBiomeSource();
        var originalClimate=world.getLevel().getChunkSource().randomState().sampler();

        // World-coordinate value noise makes adjacent chunks agree on the same broad bands.
        for (int x = minX; x < minX + 16; ++x) {
            for (int z = minZ; z < minZ + 16; ++z) {
                int top = world.getHeight(coastalTerrain ? Heightmap.Types.OCEAN_FLOOR_WG
                    : Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (top < sea - 24) continue;
                BlockPos surface = new BlockPos(x, top, z);
                boolean stony=world.getBiome(surface).is(Biomes.STONY_SHORE);
                boolean ocean=world.getBiome(surface).is(BiomeTags.IS_OCEAN);
                var detail=regional && model!=null?model.detail(x,z):null;
                boolean oceanRock=ocean && detail!=null && detail.featureInfluence()>0.001
                    && (detail.arch()!=null || detail.overhang()!=null);
                // The extension feature also visits inland/beach provider biomes. Only the
                // shared coastal plan grants material ownership there; ordinary shores are
                // finished by shore_detail, avoiding a second palette pass on those columns.
                boolean continuation=detail!=null && (detail.mask()>.001 || detail.featureInfluence()>.001);
                if(apronOnly ? stony || !continuation : !stony) continue;
                if(apronOnly && CoastalBiomeIntegration.protectedBiome(
                    CoastalBiomeIntegration.original(originalSource,originalClimate,x,top,z)))continue;
                boolean beachCap=false;int beachFloor=top;
                if(regional) {
                    // Terrain Slabs can finish before this pass; recolor its generated slab
                    // along with the supporting beach instead of rejecting the whole cap.
                    BlockState slab=world.getBlockState(surface);
                    BlockPos floor=BeachMaterials.generatedStoneSlab(slab) ? surface.below() : surface;
                    beachFloor=floor.getY();
                    beachCap=sandCap(world,floor,CoastalTerrainIntegration.sandCover(world.getLevel(),x,z,beachFloor));
                    any |= beachCap;
                }
                // Attached ocean rock bodies share the shore palette without relabelling biomes.
                if(apronOnly && ocean && !oceanRock) continue;
                double rockWeight=model==null || oceanRock?1:ShoreTransition.rockWeight(model.ground(x,z).inlandDistance());
                // Topsoil follows the adjacent inland biome and fades through broad patches.
                if(!beachCap && !ocean && rockWeight<1 && top>sea+6 && isSourceStone(world.getBlockState(surface))
                    && world.getBlockState(surface.above()).isAir()
                    && valueNoise(x+117,z-691,7)>rockWeight) {
                    BlockState cap=ShoreTransition.inlandCap(world.getLevel(),x,top,z);
                    if(!cap.is(Blocks.STONE)) {
                        BlockState substrate=cap.is(Blocks.SAND)?Blocks.SANDSTONE.defaultBlockState():
                            cap.is(Blocks.TERRACOTTA)?cap:Blocks.DIRT.defaultBlockState();
                        // Sand must have a solid substrate; no placement across a cave opening.
                        if(isSourceStone(world.getBlockState(surface.below())) && isSourceStone(world.getBlockState(surface.below(2)))) {
                            world.setBlock(surface.below(2),substrate,2);world.setBlock(surface.below(),substrate,2);
                            world.setBlock(surface,cap,2);any=true;
                        }
                    }
                }
                boolean cold = ShoreConfig.COLD.get() && isCold(world, surface);
                boolean coast = !coastalTerrain && top >= sea && top <= sea + 8
                    && world.getBlockState(surface.above()).isAir() && nearOcean(world, surface);
                if (!regional && coastalTerrain && top >= sea - 1 && top <= sea + 3) {
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
                    if(beachCap && y>=beachFloor-2)continue;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState old = world.getBlockState(pos);
                    if (!isSourceStone(old)) continue;
                    if (y != top && !hasOpenSide(world, pos)) continue;
                    if(valueNoise(x+413,z-229,7)>rockWeight)continue;
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

    private static boolean sandCap(WorldGenLevel world,BlockPos surface,double coverage) {
        int sea=world.getSeaLevel();
        if(coverage<=0) return false;
        BlockState above=world.getBlockState(surface.above());
        if(!above.isAir() && above.getFluidState().isEmpty() && !BeachMaterials.generatedStoneSlab(above)
            && !above.canBeReplaced()) return false;
        double upper=Math.max(0,Math.min(1,(surface.getY()-(sea+2))/4.0));
        coverage*=1-upper*upper*(3-2*upper);
        // A continuous field keeps the beach edge irregular without producing
        // independent, one-block teeth at the waterline or across chunk borders.
        int shift=(int)(world.getSeed() ^ (world.getSeed() >>> 32));
        double edge=valueNoise(surface.getX()+shift,surface.getZ()-shift,7);
        if(coverage<0.82 && edge>=coverage) return false;
        if(!isSandSubstrate(world.getBlockState(surface))
            || !isSandSubstrate(world.getBlockState(surface.below()))
            || !isSandSubstrate(world.getBlockState(surface.below(2)))) return false;
        // Replace supported material only. The sea-floor may be covered by aquatic plants.
        if(!world.getBlockState(surface.below(3)).isFaceSturdy(world,surface.below(3),net.minecraft.core.Direction.UP)) return false;
        boolean sand=beachSandAt(surface.getX(),surface.getZ(),world.getSeed());
        world.setBlock(surface.below(2),(sand?Blocks.SANDSTONE:Blocks.STONE).defaultBlockState(),2);
        world.setBlock(surface.below(),(sand?Blocks.SAND:Blocks.STONE).defaultBlockState(),2);
        world.setBlock(surface,(sand?Blocks.SAND:Blocks.STONE).defaultBlockState(),2);
        if(BeachMaterials.generatedStoneSlab(above)) {
            Block capSlab=optionalBlock(sand?"terrain_slabs:sand_slab":"terrain_slabs:terrain_stone_slab");
            if(capSlab!=null) world.setBlock(surface.above(),BeachMaterials.copySlab(above,capSlab),2);
        } else {
            // The material pass follows vegetation placement; remove only plants whose new
            // natural cap cannot support them, including the upper half of a tall plant.
            for(int dy=1;dy<=2;dy++) {
                BlockPos pos=surface.above(dy);BlockState plant=world.getBlockState(pos);
                if(!plant.isAir() && plant.getFluidState().isEmpty() && plant.canBeReplaced()
                    && !plant.canSurvive(world,pos))world.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            }
        }
        return true;
    }
    static boolean beachSandAt(int x,int z,long seed) {
        int shift=(int)(seed ^ (seed >>> 32));
        // Sparse connected stone outcrops; the remaining designated footprint is sandy.
        return valueNoise(x+shift+173,z-shift-389,29)<.77;
    }
    static boolean isSandSubstrate(BlockState state) {
        return !state.hasBlockEntity() && !state.is(net.minecraftforge.common.Tags.Blocks.ORES)
            && (isSourceStone(state) || state.is(Blocks.SAND) || state.is(Blocks.SANDSTONE)
            || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
            || state.is(Blocks.TUFF) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)
            || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL)
            || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK));
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

package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

/** Sparse finishing on verified natural surfaces; every write stays in its feature chunk. */
public final class ShoreDecorationPass {
    private ShoreDecorationPass() {}
    public static boolean apply(WorldGenLevel world,ChunkPos chunk) {
        if(!ShoreConfig.LANDFORMS.get() || !CoastalTerrainIntegration.installed(world.getLevel())) return false;
        boolean changed=false;
        Block pebble=optionalBlock("twigs:pebble");
        Block log=optionalBlock("quark:hollow_oak_log");
        if(log==null) log=Blocks.STRIPPED_OAK_LOG;
        for(int lx=2;lx<14;lx++) for(int lz=2;lz<14;lz++) {
            int x=chunk.getMinBlockX()+lx,z=chunk.getMinBlockZ()+lz;
            int top=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            BlockPos p=new BlockPos(x,top+1,z);
            if(!world.getBiome(p.below()).is(Biomes.STONY_SHORE)) continue;
            double roll=unit(hash(x+world.getSeed(),739,z));
            if(top>=world.getSeaLevel()-1 && top<=world.getSeaLevel()+5
                && world.getBlockState(p.below()).is(Blocks.SAND) && world.isEmptyBlock(p)) {
                if(roll<0.004) changed|=log(world,p,log,unit(hash(x,751,z))<0.5);
                else if(roll<0.012) changed|=boulder(world,p);
                else if(roll<0.026) changed|=plant(world,p,Blocks.DEAD_BUSH.defaultBlockState());
                else if(roll<0.075 && pebble!=null) changed|=plant(world,p,pebble.defaultBlockState());
            }
            var arch=CoastalTerrainIntegration.arch(world.getLevel(),x,z);
            if(arch==null) continue;
            // Check the actual roof, not a heightmap: it can be far below the cliff top.
            for(int y=world.getSeaLevel()+3;y<=world.getSeaLevel()+arch.height()+5;y++) {
                BlockPos ceiling=new BlockPos(x,y,z);
                if(arch.opening(x,y-1,z)>=0 || !isRock(world.getBlockState(ceiling))
                    || !world.isEmptyBlock(ceiling.below())) continue;
                if(roll<0.65) { world.setBlock(ceiling,Blocks.MOSSY_COBBLESTONE.defaultBlockState(),2); changed=true; }
                if(roll<0.13) {
                    int length=2+(int)(unit(hash(x,761,z))*5);
                    for(int n=1;n<=length;n++) {
                        BlockPos below=ceiling.below(n);
                        if(!world.isEmptyBlock(below) || !world.isEmptyBlock(below.below())) break;
                        boolean tip=n==length || !world.isEmptyBlock(below.below(2));
                        BlockState vine=(tip?Blocks.CAVE_VINES:Blocks.CAVE_VINES_PLANT).defaultBlockState()
                            .setValue(BlockStateProperties.BERRIES,unit(hash(x,n,z))<0.3);
                        world.setBlock(below,vine,2); changed=true;
                        if(tip) break;
                    }
                } else if(roll<0.2) changed|=plant(world,ceiling.below(),Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.UP,true));
                else if(roll<0.26) {
                    world.setBlock(ceiling.below(),Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true),2);
                    changed=true;
                }
                break;
            }
            if(Math.abs(x-arch.x())<arch.width()+7 && Math.abs(z-arch.z())<arch.length()+7
                && isRock(world.getBlockState(p.below())) && world.isEmptyBlock(p) && roll<0.18) {
                world.setBlock(p.below(),Blocks.MOSS_BLOCK.defaultBlockState(),2); changed=true;
                if(roll<0.045) changed|=plant(world,p,Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT,true));
            }
        }
        return changed;
    }
    private static boolean plant(WorldGenLevel world,BlockPos p,BlockState state) {
        if(!world.isEmptyBlock(p) || !state.canSurvive(world,p)) return false;
        return world.setBlock(p,state,2);
    }
    private static boolean log(WorldGenLevel world,BlockPos p,Block block,boolean alongX) {
        Direction direction=alongX?Direction.EAST:Direction.SOUTH;
        for(int n=0;n<3;n++) {
            BlockPos at=p.relative(direction,n);
            if(!world.isEmptyBlock(at) || !world.getBlockState(at.below()).is(Blocks.SAND)
                || !world.getBiome(at).is(Biomes.STONY_SHORE)) return false;
        }
        BlockState state=block.defaultBlockState();
        if(state.hasProperty(BlockStateProperties.AXIS)) state=state.setValue(BlockStateProperties.AXIS,alongX?Direction.Axis.X:Direction.Axis.Z);
        for(int n=0;n<3;n++) world.setBlock(p.relative(direction,n),state,2);
        return true;
    }
    private static boolean boulder(WorldGenLevel world,BlockPos p) {
        // Small uneven talus, validated completely before any writes.
        BlockPos[] footprint={p,p.east(),p.south()};
        for(BlockPos at:footprint) if(!world.isEmptyBlock(at) || !world.getBlockState(at.below()).is(Blocks.SAND)
            || !world.getBiome(at).is(Biomes.STONY_SHORE)) return false;
        for(BlockPos at:footprint) world.setBlock(at,unit(hash(at.getX(),at.getY(),at.getZ()))<0.6
            ?Blocks.MOSSY_COBBLESTONE.defaultBlockState():Blocks.ANDESITE.defaultBlockState(),2);
        if(world.isEmptyBlock(p.above()) && unit(hash(p.getX(),773,p.getZ()))<0.35)
            world.setBlock(p.above(),Blocks.MOSS_BLOCK.defaultBlockState(),2);
        return true;
    }
}

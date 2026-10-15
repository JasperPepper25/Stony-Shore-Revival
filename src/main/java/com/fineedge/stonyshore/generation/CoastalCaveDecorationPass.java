package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import com.fineedge.stonyshore.terrain.CoastalCaves;
import com.fineedge.stonyshore.terrain.CoastalBiomeIntegration;
import com.fineedge.stonyshore.terrain.CoastalTerrainIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import static com.fineedge.stonyshore.generation.ShoreBlocks.*;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

/** Finishes only planned coastal cave interiors; all writes remain inside the feature chunk. */
public final class CoastalCaveDecorationPass {
    private CoastalCaveDecorationPass() {}
    public static boolean apply(WorldGenLevel world,ChunkPos chunk) {
        if(!ShoreConfig.LANDFORMS.get() || !CoastalTerrainIntegration.installed(world.getLevel()))return false;
        boolean changed=false;
        for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++) {
            int x=chunk.getMinBlockX()+lx,z=chunk.getMinBlockZ()+lz;
            var cave=CoastalTerrainIntegration.cave(world.getLevel(),x,z);if(cave==null)continue;
            double roll=unit(hash(x+world.getSeed(),2501,z));
            int low=(int)Math.floor(cave.floor()-3),high=(int)Math.ceil(cave.floor()+cave.height()*1.13+5);
            for(int y=low;y<=high;y++) {
                BlockPos p=new BlockPos(x,y,z);BlockState state=world.getBlockState(p);
                if(!isRock(state) || CoastalBiomeIntegration.protectedBiome(world.getBiome(p)))continue;
                boolean floor=cave.opening(x,y+1,z)<-.2 && emptyOrWater(world.getBlockState(p.above()));
                boolean roof=cave.opening(x,y-1,z)<-.2 && emptyOrWater(world.getBlockState(p.below()));
                if(!floor && !roof)continue;
                if(cave.interior()==CoastalCaves.Interior.LUSH) {
                    if(roll<.72) {world.setBlock(p,Blocks.MOSS_BLOCK.defaultBlockState(),2);changed=true;}
                    if(floor && roll<.15 && world.isEmptyBlock(p.above())) {
                        var plant=(roll<.045?Blocks.FLOWERING_AZALEA:Blocks.MOSS_CARPET).defaultBlockState();
                        if(plant.canSurvive(world,p.above()))changed|=world.setBlock(p.above(),plant,2);
                    }
                    if(floor && roll<.12 && world.getBlockState(p.above()).is(Blocks.WATER)) {
                        var plant=Blocks.SEAGRASS.defaultBlockState();
                        if(plant.canSurvive(world,p.above()))changed|=world.setBlock(p.above(),plant,2);
                    }
                    if(roof && roll<.19)changed|=vine(world,p,2+(int)(unit(hash(x,2503,z))*5));
                    else if(roof && roll<.225 && world.isEmptyBlock(p.below())) {
                        var flower=Blocks.SPORE_BLOSSOM.defaultBlockState();
                        if(flower.canSurvive(world,p.below()))changed|=world.setBlock(p.below(),flower,2);
                    }
                } else if(roll<.56) {
                    world.setBlock(p,Blocks.DRIPSTONE_BLOCK.defaultBlockState(),2);changed=true;
                    if(roll<.18)changed|=dripstone(world,p,roof?Direction.DOWN:Direction.UP,
                        1+(int)(unit(hash(x,2507,z))*3));
                }
            }
        }
        return changed;
    }
    private static boolean emptyOrWater(BlockState state) { return state.isAir() || state.is(Blocks.WATER); }
    private static boolean vine(WorldGenLevel world,BlockPos ceiling,int length) {
        int free=0;
        for(int i=1;i<=length && world.isEmptyBlock(ceiling.below(i));i++)free++;
        if(free==0)return false;
        for(int i=1;i<=free;i++) {
            var state=(i==free?Blocks.CAVE_VINES:Blocks.CAVE_VINES_PLANT).defaultBlockState()
                .setValue(BlockStateProperties.BERRIES,unit(hash(ceiling.getX(),i+2511,ceiling.getZ()))<.25);
            world.setBlock(ceiling.below(i),state,2);
        }
        return true;
    }
    private static boolean dripstone(WorldGenLevel world,BlockPos anchor,Direction direction,int length) {
        int free=0;
        for(int i=1;i<=length && emptyOrWater(world.getBlockState(anchor.relative(direction,i)));i++)free++;
        if(free==0)return false;
        for(int i=1;i<=free;i++) {
            BlockPos p=anchor.relative(direction,i);
            var thickness=i==free?DripstoneThickness.TIP:i==free-1?DripstoneThickness.FRUSTUM:DripstoneThickness.BASE;
            var state=Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION,direction)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS,thickness)
                .setValue(BlockStateProperties.WATERLOGGED,world.getBlockState(p).is(Blocks.WATER));
            world.setBlock(p,state,2);
        }
        return true;
    }
}

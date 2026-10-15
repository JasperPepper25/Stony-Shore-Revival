package com.fineedge.stonyshore.generation;

import com.fineedge.stonyshore.ShoreConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import static com.fineedge.stonyshore.generation.ShoreMath.*;

/** Small enclosed beach pools, validated in full before any terrain or plant is changed. */
final class SandyPoolPass {
    private SandyPoolPass() {}
    static boolean apply(WorldGenLevel world,ChunkPos chunk,boolean apronOnly) {
        if(!ShoreConfig.POOLS.get())return false;
        long choice=hash(chunk.x+world.getSeed(),1801,chunk.z);
        if(unit(choice)>.24)return false;
        for(int attempt=0;attempt<8;attempt++) {
            long roll=hash(choice,1807+attempt,chunk.z);
            int x=chunk.getMinBlockX()+5+(int)Math.floorMod(roll,6L);
            int z=chunk.getMinBlockZ()+5+(int)Math.floorMod(roll>>>16,6L);
            var shape=new Shape(2+unit(hash(roll,1811,0))*1.2,1.8+unit(hash(roll,1813,0))*1.0,
                unit(hash(roll,1817,0))*Math.PI,roll);
            int water=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            if(water<world.getSeaLevel() || water>world.getSeaLevel()+4)continue;
            BlockPos center=new BlockPos(x,water,z);
            if(!ShoreDecorationPass.ownsColumn(world,center,apronOnly)
                || !ShoreDecorationPass.coastalSand(world,center))continue;
            int depth=1+(int)Math.floorMod(roll>>>32,2L);
            if(place(world,x,z,water,depth,shape,apronOnly))return true;
        }
        return false;
    }
    private static boolean place(WorldGenLevel world,int cx,int cz,int water,int depth,Shape shape,boolean apronOnly) {
        int extent=shape.extent();
        if(!validBasin(shape,water,depth,(dx,dz,floor)->{
            int x=cx+dx,z=cz+dz;
            int top=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            BlockPos surface=new BlockPos(x,top,z);
            if(!ShoreDecorationPass.ownsColumn(world,surface,apronOnly)
                || !ShoreDecorationPass.coastalSand(world,surface)
                || !world.isEmptyBlock(surface.above()) || top<water || top>water+1)return false;
            // Natural sediment and a complete solid liner must already exist. Slabs,
            // ores, roots, structures, existing water and cave roofs reject the pool.
            for(int y=floor-2;y<=top;y++) {
                var state=world.getBlockState(new BlockPos(x,y,z));
                if(!ShoreSurfacePass.isSandSubstrate(state) || BeachMaterials.naturalTerrainSlab(state)
                    || !state.isFaceSturdy(world,new BlockPos(x,y,z),Direction.UP))return false;
            }
            return true;
        }))return false;
        for(int dx=-extent;dx<=extent;dx++)for(int dz=-extent;dz<=extent;dz++) {
            if(!shape.wet(dx,dz))continue;
            int x=cx+dx,z=cz+dz,top=world.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
            int floor=shape.floor(water,depth,dx,dz);
            world.setBlock(new BlockPos(x,floor-1,z),Blocks.SANDSTONE.defaultBlockState(),2);
            world.setBlock(new BlockPos(x,floor,z),Blocks.SAND.defaultBlockState(),2);
            for(int y=water+1;y<=top;y++)world.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int y=floor+1;y<=water;y++)world.setBlock(new BlockPos(x,y,z),Blocks.WATER.defaultBlockState(),2);
            BlockPos plant=new BlockPos(x,floor+1,z);
            var state=unit(hash(x+world.getSeed(),floor,z))<.07?Blocks.SEA_PICKLE.defaultBlockState():
                unit(hash(x+world.getSeed(),floor,z))<.22?Blocks.SEAGRASS.defaultBlockState():null;
            if(state!=null && state.canSurvive(world,plant))world.setBlock(plant,state,2);
        }
        return true;
    }
    /** Test the full supported bed and dry containment rim before performing any writes. */
    static boolean validBasin(Shape shape,int water,int depth,SupportedCell cell) {
        int extent=shape.extent(),wet=0;
        for(int dx=-extent;dx<=extent;dx++)for(int dz=-extent;dz<=extent;dz++) {
            boolean basin=shape.wet(dx,dz);
            if(!basin && !shape.rim(dx,dz))continue;
            if(!cell.test(dx,dz,shape.floor(water,depth,dx,dz)))return false;
            if(basin)wet++;
        }
        return wet>=9;
    }
    @FunctionalInterface interface SupportedCell { boolean test(int dx,int dz,int floor); }
    record Shape(double rx,double rz,double angle,long seed) {
        int extent() {return (int)Math.ceil(Math.max(rx,rz)) + 1;}
        private double radius(int dx,int dz) {
            double c=Math.cos(angle),s=Math.sin(angle);
            return Math.hypot((dx*c+dz*s)/rx,(-dx*s+dz*c)/rz);
        }
        boolean wet(int dx,int dz) {
            double edge=(valueNoise(dx+(int)seed,dz-(int)(seed>>>32),3)-.5)*.2;
            return radius(dx,dz)<1+edge;
        }
        boolean rim(int dx,int dz) {
            return !wet(dx,dz) && (wet(dx+1,dz) || wet(dx-1,dz) || wet(dx,dz+1) || wet(dx,dz-1));
        }
        int floor(int water,int depth,int dx,int dz) {return water-depth+(depth>1 && radius(dx,dz)>.65?1:0);}
    }
}

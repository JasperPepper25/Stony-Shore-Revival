package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalCavesTest {
    @Test void blindSeaCaveHasConnectedMouthWetFloorAndSealedBack() {
        var cave=new CoastalCaves.Cave(0,0,0,32,7,12,59.2,12,2,.6,true,CoastalCaves.Interior.LUSH);
        for(int x=-23;x<12;x++) {
            assertTrue(cave.opening(x,62,0)<0,"sea reaches the chamber through the mouth: "+x);
            assertTrue(cave.opening(x,57,0)>0,"solid room floor: "+x);
        }
        assertTrue(cave.opening(-33,65,0)>0);
        assertTrue(cave.opening(13,65,0)>0);
        assertTrue(cave.opening(-12,79,0)>0);
    }
    @Test void elevatedRoomNeverReachesOceanOrDeepUnderground() {
        var cave=new CoastalCaves.Cave(0,0,Math.PI/2,24,5,9,95,9,-1,1,false,CoastalCaves.Interior.DRIPSTONE);
        assertTrue(cave.opening(0,99,-5)<0);
        assertTrue(cave.opening(0,62,-5)>0);
        assertTrue(cave.opening(15,99,-5)>0);
    }
    private CoastalCaves planner(long seed,boolean high,boolean support) {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:high?96:45,1,x,96,120,x<0);
        return new CoastalCaves(new CoastalTerrainPlanner(seed),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->support && y<ground.sample(x,z).height());
    }
    @Test void acceptsFloodedAndShelfRoomsOnlyWithSupportAndShelfAccess() {
        var sea=planner(99,false,true);var high=planner(99,true,true);var unsupported=planner(99,false,false);
        int wet=0,dry=0;
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4) {
            var a=sea.cave(x,z);var b=high.cave(x,z);
            if(a!=null && a.flooded())wet++;
            if(b!=null && !b.flooded())dry++;
            assertNull(unsupported.cave(x,z));
        }
        assertTrue(wet>0);assertTrue(dry>0,"high entrances require an existing broad outer shelf");
    }
    @Test void plansAreIndependentOfQueryOrderAndBoundedCacheEviction() {
        var a=planner(99,false,true);var b=planner(99,false,true);
        for(int z=768;z>=-768;z-=4)b.cave(4,z);
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4)assertEquals(a.cave(x,z),b.cave(x,z));
    }
    private CoastalCaves.Cave first(CoastalCaves caves) {
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4) {
            var cave=caves.cave(x,z);if(cave!=null)return cave;
        }
        throw new AssertionError("fixture must contain a supported sea cave");
    }
    @Test void aLowDryCoastalShelfCannotBeClassifiedAsASeaConnectedRoom() {
        var options=new CoastalShape.Options(true,false,false,false,0,false,false,true);
        var model=new NativeCoastalModel(99,63,192,
            (x,y,z)->((x>=24?120:x>=0?68:45)-y)*.15,
            (x,z)->x>=0 && x<96,(x,z)->x<0,(x,y,z)->x>=0 && x<96,options);
        // The ocean is beyond x=0. A low shelf separates it from the cliff at x=24;
        // the twelve-block approach cannot reach sea-level air across that solid shelf.
        for(int z=-256;z<=256;z+=4)for(int x=8;x<64;x+=4) {
            assertNull(model.cave(x,z));
            assertFalse(model.waterCandidate(x,62,z));
        }
    }
    @Test void anOriginalAirTunnelThroughTheSideWallsRejectsTheRoom() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,1,x,96,120,x<0);
        var caves=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height() && !(y>=61 && y<=65));
        // Both old support planes remain rock; an existing horizontal void intersects
        // the side and blind-back walls and must not gain an ocean connection or water.
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4)assertNull(caves.cave(x,z));
    }
    @Test void aSubmergedHeightEstimateCannotHideSolidRockAcrossTheSeaMouth() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,1,x,96,120,x<0);
        var caves=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height() || x<0 && y==62);
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4)assertNull(caves.cave(x,z));
    }
    @Test void missingRockInTheBlindBackWallRejectsThatPlan() {
        var target=first(planner(99,false,true));
        int bx=(int)Math.round(target.x()-Math.cos(target.direction())*(target.depth()+1.5)
            -Math.sin(target.direction())*target.bend()*Math.sin(Math.PI*.85));
        int bz=(int)Math.round(target.z()-Math.sin(target.direction())*(target.depth()+1.5)
            +Math.cos(target.direction())*target.bend()*Math.sin(Math.PI*.85));
        int by=(int)Math.round(target.floor()+target.height()*.5+.45);
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,1,x,96,120,x<0);
        var blocked=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height()
                && !(Math.abs(x-bx)<=1 && Math.abs(z-bz)<=1 && Math.abs(y-by)<=1));
        assertNotEquals(target,blocked.cave(target.x(),target.z()));
    }
    @Test void aRoomCannotEndWithACutWallAtTheCoastalMaskBoundary() {
        var target=first(planner(99,false,true));
        int boundary=(int)Math.round(target.x()-Math.cos(target.direction())*(target.depth()+1.5));
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,
            x>=boundary?0:1,x,96,120,x<0);
        var blocked=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height());
        assertNotEquals(target,blocked.cave(target.x(),target.z()));
    }
    @Test void aProtectedInteriorVetoRejectsTheWholeRoomIncludingUnprotectedMouthColumns() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,1,x,96,120,x<0);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var blocked=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height(),(x,y,z)->{calls.incrementAndGet();return x<0;});
        first(planner(99,false,true)); // The same unprotected fixture has real supported rooms.
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4)assertNull(blocked.cave(x,z));
        assertTrue(calls.get()>0,"structurally valid rooms reach original-biome validation");
    }
    @Test void aProtectedPortalVetoRejectsTheRoomEvenWhenItsRockInteriorIsAllowed() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(x>=0?120:45,1,x,96,120,x<0);
        var blocked=new CoastalCaves(new CoastalTerrainPlanner(99),63,ground,(x,z)->ground.sample(x,z).height(),
            (x,y,z)->y<ground.sample(x,z).height(),(x,y,z)->x>=0);
        for(int z=-256;z<=256;z+=4)for(int x=-12;x<48;x+=4)assertNull(blocked.cave(x,z));
    }
}

package com.fineedge.stonyshore.terrain;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalPoolsTest {
    private static CoastalPools flat(long seed,double level,CoastalShape.Solid support) {
        return new CoastalPools(new CoastalTerrainPlanner(seed),63,
            (x,z)->new CoastalShape.Ground(level,1,24),(x,z)->level,support);
    }

    @Test void shallowPoolsKeepVisibleWetAreaAndLeaveOuterTerrainUntouched() {
        var noise=new CoastalTerrainPlanner(42);
        var pool=new CoastalLandforms.Pool(-13,17,4,3.2,.7,90,1,101);
        int wet=0,minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        for(int x=-23;x<=-3;x++)for(int z=7;z<=27;z++) {
            double floor=pool.floor(x,z,91,noise);
            assertTrue(floor<=91,"A pool must not construct a raised shelf");
            assertTrue(floor>=88.25);
            if(pool.radius(x,z,noise)>=1)assertEquals(91,floor,1e-9);
            if(pool.wetAt(x,z,91,noise)) {
                wet++;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                assertTrue(floor+.5<=89+1e-9,"Every counted column can hold the top water block");
            }
        }
        assertTrue(wet>=12,"A depth-one pool must not collapse to a single wet column");
        assertTrue(maxX-minX>=3 && maxZ-minZ>=3);
        assertEquals(88.25,pool.floor(pool.x(),pool.z(),91,noise),1e-9);
        assertEquals(-.25,pool.floor(pool.x(),pool.z(),91,noise)+.5-(pool.water()-1),1e-9,
            "The first shallow water voxel needs negative density margin");
    }

    @Test void repeatedRegionsCanHaveTwoSeparatedPoolsAndAllSizeClasses() {
        int pairs=0;boolean small=false,medium=false,large=false;
        for(int seed=0;seed<80;seed++) {
            var plans=flat(seed,110,(x,y,z)->y<110.5).plan(-1,2);
            assertTrue(plans.size()<=2);
            if(plans.size()==2) {
                pairs++;
                var a=plans.get(0);var b=plans.get(1);
                assertTrue(Math.hypot(a.x()-b.x(),a.z()-b.z())>=a.footprintRadius()+b.footprintRadius()+2);
            }
            for(var p:plans) {
                small|=p.rx()<5;medium|=p.rx()>=5 && p.rx()<8;large|=p.rx()>=8;
            }
        }
        assertTrue(pairs>0,"Local shelves can host multiple basins");
        assertTrue(small && medium && large,"Readable small, medium and large pools must remain possible");
    }

    @Test void separateCliffTerracesKeepTheirOwnWaterElevations() {
        var levels=new HashSet<Integer>();
        for(int seed=0;seed<80;seed++) {
            CoastalPools.Height terraces=(x,z)->x<24?90:140;
            var pools=new CoastalPools(new CoastalTerrainPlanner(seed),63,
                (x,z)->new CoastalShape.Ground(terraces.sample(x,z),1,24),terraces,
                (x,y,z)->y<terraces.sample(x,z)+.5);
            for(var p:pools.plan(0,0)) {
                levels.add(p.water());
                assertEquals((int)Math.floor(terraces.sample(p.x(),p.z())-.2),p.water());
            }
        }
        assertTrue(levels.contains(89) && levels.contains(139),"Pools must fit both shelves without flattening the cliff");
    }

    @Test void unsupportedFloorsAndUncontainedSlopesRejectCompletePools() {
        for(int seed=0;seed<12;seed++) {
            assertTrue(flat(seed,110,(x,y,z)->false).plan(0,0).isEmpty(),"Do not fill caves with a pool liner");
            assertTrue(flat(seed,110,(x,y,z)->y<110.5 && y!=108).plan(0,0).isEmpty(),
                "A cave-punctured rim must reject the basin even when rock exists farther below");
            CoastalPools.Height slope=(x,z)->110+x*.65;
            var steep=new CoastalPools(new CoastalTerrainPlanner(seed),63,
                (x,z)->new CoastalShape.Ground(slope.sample(x,z),1,24),slope,
                (x,y,z)->y<slope.sample(x,z)+.5);
            assertTrue(steep.plan(0,0).isEmpty(),"Reject slopes rather than excavating an artificial flat terrace");
        }
    }

    @Test void seedAndNegativeRegionCoordinatesAreIndependentOfQueryOrder() {
        var forward=flat(81,110,(x,y,z)->y<110.5);
        var reverse=flat(81,110,(x,y,z)->y<110.5);
        for(int x=3;x>=-3;x--)for(int z=3;z>=-3;z--)reverse.plan(x,z);
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)assertEquals(forward.plan(x,z),reverse.plan(x,z));
    }
}

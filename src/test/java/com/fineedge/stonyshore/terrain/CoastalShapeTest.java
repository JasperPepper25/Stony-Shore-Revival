package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalShapeTest {
    private static CoastalShape shape(long seed,CoastalShape.GroundSampler sampler,boolean pools,boolean arches) {
        return new CoastalShape(seed,63,sampler,new CoastalShape.Options(true,pools,arches,false,0));
    }
    @Test void preservesOtherBiomesAndFadesContinuouslyAtOceanBoundary() {
        var shape=shape(42,(x,z)->new CoastalShape.Ground(100,x<=0?0:CoastalShape.smooth(x/12.0),24),true,false);
        for(int y=40;y<140;y++)assertEquals(0.2,shape.density(0.2,-1,y,0,.15));
        double outside=shape.density(0.2,0,90,0,.15),justInside=shape.density(0.2,1,90,0,.15);
        assertTrue(Math.abs(outside-justInside)<0.03,"boundary must not leave a wall at the biome gate");
        assertFalse(shape.waterCandidate(0,62,0));
    }
    @Test void preservesShallowAndDeepCaveAirBelowTheOriginalSurface() {
        var shape=shape(42,(x,z)->new CoastalShape.Ground(110,1,96),false,false);
        assertEquals(-0.2,shape.density(-0.2,20,64,20,.15),1e-9);
        assertEquals(-0.2,shape.density(-0.2,20,39,20,.15),1e-9);
    }
    @Test void elevatedPoolsHaveSolidFloorsAndNoDryGapUnderTheirWater() {
        var shape=shape(42,(x,z)->new CoastalShape.Ground(110,1,96),true,false);
        CoastalShape.Column found=null;int px=0,pz=0;
        outer:for(int x=-160;x<160;x+=2)for(int z=-160;z<160;z+=2) {
            var c=shape.column(x,z);
            if(c.pool()!=null && x==c.pool().x() && z==c.pool().z()){found=c;px=x;pz=z;break outer;}
        }
        // Region centers can be odd; use an interior pool sample and query its exact center.
        if(found==null)outer:for(int x=-160;x<160;x+=4)for(int z=-160;z<160;z+=4) {
            var c=shape.column(x,z);if(c.pool()!=null){px=c.pool().x();pz=c.pool().z();found=shape.column(px,pz);break outer;}
        }
        assertNotNull(found);assertTrue(found.water()>63);
        int first=found.water()-found.pool().depth();
        assertTrue(shape.density(-1,px,first-1,pz,.15)>0);
        for(int y=first;y<found.water();y++) {
            assertTrue(shape.density(1,px,y,pz,.15)<=0);assertTrue(shape.waterCandidate(px,y,pz));
        }
        assertFalse(shape.waterCandidate(px,found.water(),pz));
    }
    @Test void seaLevelWaterPlansDoNotConflictWithSurfaceRoughness() {
        var shape=shape(123,(x,z)->new CoastalShape.Ground(64,1,24),true,false);
        int planned=0;
        for(int x=-128;x<=128;x+=4)for(int z=-128;z<=128;z+=4)for(int y=60;y<63;y++)
            if(shape.waterCandidate(x,y,z)) {
                planned++;assertTrue(shape.density(1,x,y,z,.15)<=0,"planned water must occupy air density");
            }
        assertTrue(planned>100);
    }
    @Test void orderAndCacheEvictionDoNotChangeTerrain() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(110+x*.06,1,24);
        var a=shape(99,ground,true,true);var b=shape(99,ground,true,true);
        for(int x=768;x>=-768;x-=8)for(int z=768;z>=-768;z-=8)b.column(x,z);
        for(int x=-160;x<=160;x+=8)for(int z=-160;z<=160;z+=8)
            assertEquals(a.density(0.1,x,78,z,.15),b.density(0.1,x,78,z,.15),1e-9);
    }
    @Test void sharedCacheRemainsBoundedAndDoesNotHoldTheFactoryMonitor() {
        var cache=new BoundedCache<Integer,Integer>(2);
        assertEquals(3,cache.get(1,k->cache.get(2,n->2)+1));
        cache.get(3,k->3);assertEquals(2,cache.size());
    }
}

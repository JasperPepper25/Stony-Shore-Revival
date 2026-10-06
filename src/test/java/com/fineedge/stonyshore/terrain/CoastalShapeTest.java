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
    @Test void canConstructGroundInOriginalAirAndPreservesDeepCaves() {
        var shape=shape(42,(x,z)->new CoastalShape.Ground(110,1,96),false,false);
        assertTrue(shape.density(-0.2,20,64,20,.15)>0,"new coastal body can contain rock where the original graph was air");
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
    @Test void orderAndCacheEvictionDoNotChangeTerrain() {
        var ground=(CoastalShape.GroundSampler)(x,z)->new CoastalShape.Ground(110+x*.06,1,24);
        var a=shape(99,ground,true,true);var b=shape(99,ground,true,true);
        for(int x=768;x>=-768;x-=8)for(int z=768;z>=-768;z-=8)b.column(x,z);
        for(int x=-160;x<=160;x+=8)for(int z=-160;z<=160;z+=8)
            assertEquals(a.density(0.1,x,78,z,.15),b.density(0.1,x,78,z,.15),1e-9);
    }
    @Test void archApproachesAreOpenAndItsRoofIsSolid() {
        var shape=shape(123,(x,z)->new CoastalShape.Ground(110,1,24),false,true);
        CoastalLandforms.Arch a=null;
        outer:for(int x=-240;x<240;x+=8)for(int z=-240;z<240;z+=8)if(shape.column(x,z).arch()!=null){a=shape.column(x,z).arch();break outer;}
        assertNotNull(a);
        assertTrue(shape.density(1,a.x(),(int)(63+a.height()*.43),a.z(),.15)<0);
        assertTrue(shape.density(-1,a.x(),(int)Math.ceil(63+a.height()+3),a.z(),.15)>0);
        for(int sign:new int[]{-1,1}) {
            int x=(int)Math.round(a.x()-sign*Math.sin(a.angle())*(a.length()+2));
            int z=(int)Math.round(a.z()+sign*Math.cos(a.angle())*(a.length()+2));
            assertTrue(shape.density(1,x,(int)(63+a.height()*.43),z,.15)<0,"arch portal must lead to exterior air");
        }
    }
    @Test void overhangsRetainRoofsAndOpenToTheDescendingExterior() {
        var shape=shape(123,(x,z)->new CoastalShape.Ground(180+x*0.25,1,24),false,false);
        CoastalLandforms.Overhang a=null;
        outer:for(int x=-240;x<240;x+=8)for(int z=-240;z<240;z+=8)
            if(shape.column(x,z).overhang()!=null){a=shape.column(x,z).overhang();break outer;}
        assertNotNull(a);
        assertTrue(shape.density(1,a.x(),a.floor()+5,a.z(),.15)<0);
        assertTrue(shape.density(-1,a.x(),a.floor()+(int)Math.ceil(a.height())+1,a.z(),.15)>0);
        for(int v=0;v<=a.reach()+8;v++) {
            int x=(int)Math.round(a.x()-Math.sin(a.angle())*v);
            int z=(int)Math.round(a.z()+Math.cos(a.angle())*v);
            assertTrue(shape.density(1,x,a.floor()+5,z,.15)<0,"recess must connect to exterior air");
        }
    }
    @Test void sharedCacheRemainsBoundedAndDoesNotHoldTheFactoryMonitor() {
        var cache=new BoundedCache<Integer,Integer>(2);
        assertEquals(3,cache.get(1,k->cache.get(2,n->2)+1));
        cache.get(3,k->3);assertEquals(2,cache.size());
    }
}

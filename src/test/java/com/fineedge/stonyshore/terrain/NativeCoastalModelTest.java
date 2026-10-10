package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeCoastalModelTest {
    @Test void heightGridCachesProbesAndBiomeBoundaryIncludesOcean() {
        var probes=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->{probes.incrementAndGet();return ((x>=0?110:45)-y)*.15;},
            (x,z)->x>=0,(x,z)->x<0,(x,y,z)->x>=0,new CoastalShape.Options(false,true,false,false,0));
        assertEquals(109.5,model.originalHeight(24,24),1e-9);
        int initial=probes.get();assertTrue(initial<=16*24,"sixteen cubic nodes use bounded eight-block surface searches");
        for(int i=0;i<100;i++)model.originalHeight(24,24);
        assertEquals(initial,probes.get());
        assertTrue(model.ground(0,24).mask()>.5);assertEquals(1,model.ground(24,24).mask());
        assertEquals(.2,model.cap(.2,-128,90,24,.15));
    }
    @Test void doesNotQuerySubterraneanBiomesForTheSurfaceFootprint() {
        var queries=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->((x>=0?110:45)-y)*.15,(x,z)->x>=0,(x,z)->x<0,
            (x,y,z)->{queries.incrementAndGet();return true;},new CoastalShape.Options(false,true,false,false,0));
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)assertEquals(1,model.ground(x,z).mask());
        assertEquals(0,queries.get(),"surface climate predicates already define the horizontal footprint");
    }
    @Test void subterraneanBiomeDoesNotSuppressSurfaceClimateCoast() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->((x>=0?110:45)-y)*.15,(x,z)->x>=0,(x,z)->x<0,
            (x,y,z)->false,new CoastalShape.Options(true,true,true,true,.65));
        assertEquals(1,model.ground(24,24).mask());assertTrue(model.column(24,24).active());
        assertNotEquals(.2,model.cap(.2,24,108,24,.15),"surface climate coast participates in shaping");
        assertFalse(model.waterCandidate(24,62,24));
    }
    @Test void actualHighSurfaceShoreSupplementsTheClimateProjection() {
        var queries=new AtomicInteger();
        var model=new NativeCoastalModel(11,63,192,(x,y,z)->((x>=0?110:45)-y)*.15,
            (x,z)->false,(x,z)->x<0,(x,y,z)->{queries.incrementAndGet();return x>=0 && y>=100;},
            new CoastalShape.Options(false,false,false,false,0));
        assertTrue(model.shoreColumn(24,24),"the actual high terrain top is stony shore");
        assertEquals(1,model.ground(24,24).mask());
        int cached=queries.get();
        for(int i=0;i<30;i++)assertTrue(model.shoreColumn(24,24));
        assertEquals(cached,queries.get(),"classification and its top-height probe are cached");
    }
    @Test void seaLevelCaveShoreCannotExpandTheFootprintOntoAnInlandSurface() {
        var model=new NativeCoastalModel(11,63,192,(x,y,z)->(110-y)*.15,
            (x,z)->false,(x,z)->false,(x,y,z)->y<80,
            new CoastalShape.Options(false,false,false,true,1));
        assertFalse(model.shoreColumn(24,24));
        assertEquals(0,model.ground(24,24).mask());
        assertEquals(.25,model.cap(.25,24,108,24,.15));
    }
    @Test void signedOceanReachHasAHighShoreAnchorAndTapersBeforeItsHardBoundary() {
        var model=new NativeCoastalModel(19,63,192,(x,y,z)->((x>=0?120:30)-y)*.15,
            (x,z)->x>=0,(x,z)->x<0,(x,y,z)->x>=0,
            new CoastalShape.Options(false,false,false,true,1));
        var inner=model.ground(-24,0);
        var outer=model.ground(-92,0);
        var outside=model.ground(-128,0);
        assertTrue(inner.ocean());assertTrue(inner.oceanDistance()<0);
        assertTrue(inner.shoreHeight()>100);assertTrue(inner.height()<63);
        assertTrue(inner.mask()>.95);
        assertTrue(outer.mask()>0 && outer.mask()<inner.mask());
        assertEquals(0,outside.mask());
        assertEquals(-.5,model.cap(-.5,-128,50,0,.15));
    }
    @Test void raisedShallowSeabedConnectsToDeepOriginalFloorBelowTheOldVerticalLimit() {
        var model=new NativeCoastalModel(19,63,192,(x,y,z)->((x>=0?120:30)-y)*.15,
            (x,z)->x>=0,(x,z)->x<0,(x,y,z)->x>=0,
            new CoastalShape.Options(false,false,false,true,1));
        int selected=Integer.MIN_VALUE;
        for(int z=-1024;z<=1024;z+=16)if(model.beachField(-24,z)>.99) {selected=z;break;}
        assertNotEquals(Integer.MIN_VALUE,selected,"the selected coast contains a beach");
        assertTrue(model.detail(-24,selected).surface()>50);
        assertTrue(model.cap((30-35)*.15,-24,35,selected,.15)>0,
            "the shelf body joins its original floor rather than floating above sea−24");
    }
    @Test void completeNativeRockVolumesRemainActivePastTheLocalEligibilityMask() {
        // The physical cliff lies near x=8; the provider's narrow rocky biome lies much
        // farther inland. Its eligible coast can originate an arch whose rounded end
        // extends past the ordinary biome influence, while remaining near the same sea.
        var model=new NativeCoastalModel(456,63,192,(x,y,z)->((x>=8?110:45)-y)*.15,
            (x,z)->x>=96 && x<=112,(x,z)->x<8,(x,y,z)->false,
            new CoastalShape.Options(true,false,true,false,0));
        int complete=0;
        for(int x=-36;x<=-32;x++)for(int z=-2048;z<=2048;z+=2) {
            var detail=model.detail(x,z);
            if(detail.mask()!=0 || detail.featureInfluence()<=0 || detail.arch()==null)continue;
            var fin=detail.arch().fin();
            int y=(int)Math.round((fin.top(x,z)+fin.bottom(x,z))*.5);
            if(fin.solid(x,y,z)<=0 || detail.arch().opening(x,y,z)<0)continue;
            complete++;
            assertTrue(model.column(x,z).active(),"surface ownership follows the accepted volume across mask zero");
            assertTrue(model.cap((45-y)*.15,x,y,z,.15)>0,"the rounded pier remains actual rock");
            assertEquals(0,model.column(x,z).sandStrength());
        }
        assertTrue(complete>0,()->"fixture must exercise real solid rock beyond the eligibility boundary: "
            +model.landformStats()+" root8="+model.ground(8,0)+" root12="+model.ground(12,0)+" tip="+model.ground(-33,0));
        assertFalse(model.column(-160,0).active());
    }
}

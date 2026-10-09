package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeCoastalModelTest {
    @Test void heightGridCachesProbesAndBiomeBoundaryIncludesOcean() {
        var probes=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->{probes.incrementAndGet();return (110-y)*.15;},
            (x,z)->x>=0,(x,z)->x<0,(x,y,z)->x>=0,new CoastalShape.Options(false,true,false,false,0));
        assertEquals(109.5,model.originalHeight(24,24),1e-9);
        int initial=probes.get();assertTrue(initial<240);
        for(int i=0;i<100;i++)model.originalHeight(24,24);
        assertEquals(initial,probes.get());
        assertTrue(model.ground(0,24).mask()>.5);assertEquals(1,model.ground(24,24).mask());
        assertEquals(.2,model.cap(.2,-80,90,24,.15));
    }
    @Test void doesNotQuerySubterraneanBiomesForTheSurfaceFootprint() {
        var queries=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,(x,z)->true,(x,z)->false,
            (x,y,z)->{queries.incrementAndGet();return true;},new CoastalShape.Options(false,true,false,false,0));
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)assertEquals(1,model.ground(x,z).mask());
        assertEquals(0,queries.get(),"surface climate predicates already define the horizontal footprint");
    }
    @Test void subterraneanBiomeDoesNotSuppressSurfaceClimateCoast() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,(x,z)->true,(x,z)->false,
            (x,y,z)->false,new CoastalShape.Options(true,true,true,true,.65));
        assertEquals(1,model.ground(24,24).mask());assertTrue(model.column(24,24).active());
        assertNotEquals(.2,model.cap(.2,24,108,24,.15),"surface climate coast participates in shaping");
        assertFalse(model.waterCandidate(24,62,24));
    }
    @Test void actualHighSurfaceShoreSupplementsTheClimateProjection() {
        var queries=new AtomicInteger();
        var model=new NativeCoastalModel(11,63,192,(x,y,z)->(110-y)*.15,
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
        var outer=model.ground(-52,0);
        var outside=model.ground(-80,0);
        assertTrue(inner.ocean());assertTrue(inner.oceanDistance()<0);
        assertTrue(inner.shoreHeight()>100);assertTrue(inner.height()<63);
        assertTrue(inner.mask()>.95);
        assertTrue(outer.mask()>0 && outer.mask()<inner.mask());
        assertEquals(0,outside.mask());
        assertEquals(-.5,model.cap(-.5,-80,50,0,.15));
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
}

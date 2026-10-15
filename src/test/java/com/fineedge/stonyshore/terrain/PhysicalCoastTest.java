package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalCoastTest {
    private static CoastalShape.Options options() { return new CoastalShape.Options(false,false,false,true,1); }
    private static double terrain(int x,int y,int z) { return (63+1.2*x+5*Math.sin(z/50.0)-y)*.15; }
    private static NativeCoastalModel model(int reach) {
        return new NativeCoastalModel(19,63,192,PhysicalCoastTest::terrain,
            (x,z)->x>=-24 && x<=48,(x,z)->x<-24,(x,y,z)->false,options(),PhysicalCoastTest::terrain,reach);
    }
    @Test void immutableFinalTerrainIncludesUpstreamAdditionsAndCachesHeightProbes() {
        var probes=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(90-y)*.15,
            (x,z)->true,(x,z)->false,(x,y,z)->true,options(),
            (x,y,z)->{probes.incrementAndGet();return (120-y)*.15;});
        assertEquals(119.5,model.originalHeight(24,24),1e-9);
        int initial=probes.get();
        for(int i=0;i<100;i++)assertEquals(119.5,model.originalHeight(24,24),1e-9);
        assertEquals(initial,probes.get());
    }
    @Test void aBiomeLabelWithoutAPhysicalOceanCoastCannotCreateSandPads() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,
            (x,z)->true,(x,z)->false,(x,y,z)->false,options());
        assertEquals(0,model.ground(24,24).mask());
        assertEquals(.2,model.cap(.2,24,90,24,.15));
        assertFalse(model.waterCandidate(24,62,24));
    }
    @Test void biomeSeamsDoNotDefineThePhysicalCoastOrClipItsApron() {
        var a=model(96);
        var b=new NativeCoastalModel(19,63,192,PhysicalCoastTest::terrain,
            (x,z)->x>=-8 && x<=64,(x,z)->x<-8,(x,y,z)->false,options());
        for(int x=-48;x<=24;x++) {
            var ga=a.ground(x,20);var gb=b.ground(x,20);
            assertEquals(ga.oceanDistance(),gb.oceanDistance(),1e-8);
            assertEquals(ga.mask(),gb.mask(),.01);
            assertEquals(a.detail(x,20).terrainSurface(),b.detail(x,20).terrainSurface(),.3);
        }
        assertTrue(a.ground(-24,20).mask()>.95,"submerged shore labels still share the continuous apron");
    }
    @Test void coastalTerrainCanContinueOntoNeighboringInlandBiomesWithConfigurableWidth() {
        var narrow=model(48);var broad=model(96);
        assertEquals(0,narrow.ground(64,0).mask());
        assertTrue(broad.ground(64,0).mask()>.9);
        assertTrue(broad.shoreColumn(64,0));
        assertEquals(0,broad.ground(112,0).mask());
    }
    @Test void beachSeabedAndReliefRemainContinuousAcrossBiomeAndPlanningCells() {
        var model=model(96);
        for(int z:new int[]{-129,-1,0,127,128})for(int x=-60;x<40;x++) {
            var a=model.ground(x,z);var b=model.ground(x+1,z);
            assertTrue(Math.abs(a.oceanDistance()-b.oceanDistance())<1.5);
            assertTrue(Math.abs(a.shoreHeight()-b.shoreHeight())<1.5);
            double ah=model.detail(x,z).terrainSurface(),bh=model.detail(x+1,z).terrainSurface();
            // A cliff behind the toe may deliberately rise steeply. The shallow apron
            // must stay gradual, and the same profile must continue across tile seams.
            if(a.oceanDistance()<8 && b.oceanDistance()<8)
                assertTrue(Math.abs(ah-bh)<1.5,"shallow profile jump at "+x+","+z);
            assertTrue(Math.abs(ah-model.detail(x,z+1).terrainSurface())<1.5,
                "planning seam at "+x+","+z);
        }
    }
    @Test void distantOceanAndUnrelatedCaveBiomesRemainUntouched() {
        var model=model(96);
        assertEquals(0,model.ground(-128,0).mask());
        assertEquals(-.5,model.cap(-.5,-128,50,0,.15));
        var cave=new NativeCoastalModel(11,63,192,PhysicalCoastTest::terrain,
            (x,z)->false,(x,z)->x<0,(x,y,z)->y<40,options());
        assertEquals(0,cave.ground(24,24).mask());
    }
    @Test void shorelineIsIndependentOfTraversalAndCacheEviction() {
        var a=model(96);var b=model(96);
        for(int x=80;x>=-80;x-=4)b.ground(x,-127);
        for(int x=-80;x<=80;x+=4)assertEquals(a.ground(x,-127),b.ground(x,-127));
        for(int x=0;x<18000;x+=8)b.ground(x,1500);
        for(int x=-80;x<=80;x+=4)assertEquals(a.ground(x,-127),b.ground(x,-127));
    }
}

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
        int initial=probes.get();assertTrue(initial<60);
        for(int i=0;i<100;i++)model.originalHeight(24,24);
        assertEquals(initial,probes.get());
        assertEquals(0,model.ground(0,24).mask());assertEquals(1,model.ground(24,24).mask());
        assertEquals(.2,model.cap(.2,-1,90,24,.15));
    }
    @Test void reusesSurfaceBiomeQueriesAcrossNeighboringColumns() {
        var queries=new AtomicInteger();
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,(x,z)->true,(x,z)->false,
            (x,y,z)->{queries.incrementAndGet();return true;},new CoastalShape.Options(false,true,false,false,0));
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)assertEquals(1,model.ground(x,z).mask());
        assertTrue(queries.get()<450,"expanded inland masks must reuse bounded quart climate samples");
    }
    @Test void respectsTheBiomeAtOriginalSurfaceAsWellAsSeaLevel() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(110-y)*.15,(x,z)->true,(x,z)->false,
            (x,y,z)->false,new CoastalShape.Options(true,true,true,true,.65));
        assertEquals(0,model.ground(24,24).mask());assertEquals(.2,model.cap(.2,24,90,24,.15));
        assertFalse(model.waterCandidate(24,62,24));
    }
}

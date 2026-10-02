package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CoastalColumnSamplerTest {
    private static double flat(int x, int y, int z) { return (67.5 - y) * 0.15; }
    private static CoastalColumnSampler sampler(CoastalColumnSampler.Terrain terrain, CoastalColumnSampler.Shore shore) {
        return new CoastalColumnSampler(42, 63, terrain, shore);
    }
    @Test void nonShoreAndVerticalExclusionsAreExactIdentity() {
        var excluded = sampler(CoastalColumnSamplerTest::flat, (x,z) -> false);
        var shore = sampler(CoastalColumnSamplerTest::flat, (x,z) -> true);
        for (int y = -64; y < 150; y++) {
            double original = flat(0, y, 0);
            assertEquals(original, excluded.cap(original, 0, y, 0, 0.15));
            if (y < 60 || y > 77) assertEquals(original, shore.cap(original, 0, y, 0, 0.15));
        }
    }
    @Test void highCoastsUnderwaterAndThinFloorsAreRejected() {
        assertFalse(sampler((x,y,z) -> 100-y, (x,z) -> true).column(0,0).active());
        assertFalse(sampler((x,y,z) -> 60-y, (x,z) -> true).column(0,0).active());
        assertFalse(sampler((x,y,z) -> y==58 ? -1 : flat(x,y,z), (x,z) -> true).column(0,0).active());
    }
    @Test void carvesOnlyAndRetainsNegativeCaveDensity() {
        var shore = sampler(CoastalColumnSamplerTest::flat, (x,z) -> true);
        int active = 0, submerged = 0, dry = 0;
        for (int x=-96;x<96;x+=3) for(int z=-96;z<96;z+=3) {
            var column=shore.column(x,z);
            if(column.active()) active++;
            if(column.surface()<62.5) submerged++; else dry++;
            assertTrue(column.surface() >= 60);
            assertTrue(column.surface() <= 67);
            for(int y=59;y<=78;y++) {
                double original=flat(x,y,z);
                assertTrue(shore.cap(original,x,y,z,0.15)<=original);
                assertTrue(shore.cap(-20,x,y,z,0.15)<=-20);
            }
        }
        assertTrue(active>0); assertTrue(submerged>0); assertTrue(dry>0);
    }
    @Test void cacheEvictionAndTraversalDoNotChangeThePlan() {
        var a=sampler(CoastalColumnSamplerTest::flat,(x,z)->true);
        var expected=a.column(-17,-33);
        for(int x=-2048;x<2048;x++) a.column(x,0);
        assertEquals(expected,a.column(-17,-33));
        var b=sampler(CoastalColumnSamplerTest::flat,(x,z)->true);
        for(int cx=2;cx>=-3;cx--) for(int x=cx*16+15;x>=cx*16;x--)
            assertEquals(a.column(x,-1),b.column(x,-1));
    }
    @Test void samplingDoesNotRepeatForEveryYAndBoundaryMaskDoesNotSpill() {
        var calls=new AtomicInteger();
        var a=sampler((x,y,z)->{calls.incrementAndGet();return flat(x,y,z);},(x,z)->x<0);
        a.column(-32,0); int initial=calls.get();
        for(int y=60;y<=77;y++) a.cap(flat(-32,y,0),-32,y,0,0.15);
        assertEquals(initial,calls.get());
        for(int x=0;x<16;x++) assertFalse(a.column(x,0).active());
    }
    @Test void nonFiniteBaselineIsReported() {
        assertThrows(IllegalStateException.class,()->sampler((x,y,z)->Double.NaN,(x,z)->true).column(0,0));
    }
}

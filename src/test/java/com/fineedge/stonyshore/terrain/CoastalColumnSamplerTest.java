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
            if (y < 60 || y >= 87) assertEquals(original, shore.cap(original, 0, y, 0, 0.15));
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
            assertTrue(column.surface() >= 59.5);
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
    @Test void formerHeightCutoffNowFadesContinuously() {
        // These surfaces straddle the old solid-at-Y77 guard. They must not switch from
        // several blocks of lowering to unchanged in a fraction of a block.
        for (int x = -48; x < 48; x += 3) {
            var a = sampler((px,y,z) -> (76.99-y)*0.15, (px,z) -> true).column(x,0);
            var b = sampler((px,y,z) -> (77.01-y)*0.15, (px,z) -> true).column(x,0);
            assertTrue(Math.abs(a.surface()-b.surface()) < 0.08);
        }
        var high = sampler((x,y,z) -> (80.99-y)*0.15, (x,z) -> true);
        assertFalse(high.column(0,0).active());
    }
    @Test void fractionalHeightChangesDoNotBecomeOneBlockSteps() {
        var a = sampler((x,y,z) -> (74.999-y)*0.15, (x,z) -> true).column(0,0);
        var b = sampler((x,y,z) -> (75.001-y)*0.15, (x,z) -> true).column(0,0);
        assertTrue(Math.abs(a.surface()-b.surface()) < 0.01);
    }
    @Test void biomeEdgeHasNoQuartSizedStepsOrSpill() {
        var a = sampler(CoastalColumnSamplerTest::flat, (x,z) -> x < 0);
        double previous = a.column(-13, 0).surface();
        for (int x=-12; x<0; x++) {
            double next = a.column(x,0).surface();
            assertTrue(Math.abs(next-previous) < 1.3, "edge at " + x);
            previous = next;
        }
        assertTrue(Math.abs(67-previous) < 0.05);
        assertFalse(a.column(0,0).active());
    }
    @Test void waterOwnershipExcludesDryRidgesExistingCavesAndDeepWater() {
        var a = sampler(CoastalColumnSamplerTest::flat, (x,z) -> true);
        int wet = 0, dry = 0;
        for (int x=-128; x<128; x+=4) for (int z=-128; z<128; z+=4) {
            if (a.waterCandidate(x,62,z)) wet++; else dry++;
            assertFalse(a.waterCandidate(x,63,z));
            assertFalse(a.waterCandidate(x,59,z));
        }
        assertTrue(wet > 0); assertTrue(dry > 0);
        var cave = sampler((x,y,z) -> y == 62 ? -1 : flat(x,y,z), (x,z)->true);
        assertFalse(cave.waterCandidate(0,62,0));
        assertFalse(sampler(CoastalColumnSamplerTest::flat,(x,z)->false).waterCandidate(0,62,0));
    }
    @Test void densityReturnsSmoothlyToActualBaselineAboveTheOldBand() {
        var a = sampler(CoastalColumnSamplerTest::flat, (x,z) -> true);
        assertTrue(a.cap(0.2,0,77,0,0.15) < 0.2);
        assertTrue(Math.abs(0.2-a.cap(0.2,0,86,0,0.15)) < 0.05);
        assertEquals(0.2,a.cap(0.2,0,87,0,0.15));
    }
    @Test void nonFiniteBaselineIsReported() {
        assertThrows(IllegalStateException.class,()->sampler((x,y,z)->Double.NaN,(x,z)->true).column(0,0));
    }
}

package com.fineedge.stonyshore.generation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoreTransitionTest {
    @Test void excludesOnlyRequestedClusterBlocksWithinShoreColumns() {
        for(String id:new String[]{"quark:jasper","quark:shale","quark:limestone"}) {
            assertTrue(QuarkStonePolicy.excluded(id,true,false));
            assertTrue(QuarkStonePolicy.excluded(id,false,true));
            assertFalse(QuarkStonePolicy.excluded(id,false,false));
        }
        for(String id:new String[]{"quark:myalite","minecraft:calcite","quark:jasper_bricks","quark:hollow_oak_log"})
            assertFalse(QuarkStonePolicy.excluded(id,true,true));
    }
    @Test void paletteIntensityFadesContinuouslyTowardInlandBoundary() {
        assertEquals(0,ShoreTransition.rockWeight(0));assertEquals(1,ShoreTransition.rockWeight(24));
        assertEquals(.5,ShoreTransition.rockWeight(12));
        assertTrue(ShoreTransition.rockWeight(1)<.01);
    }
}

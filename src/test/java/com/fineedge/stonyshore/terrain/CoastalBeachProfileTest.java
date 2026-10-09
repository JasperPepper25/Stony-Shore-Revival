package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalBeachProfileTest {
    private static final int SEA=63;
    private CoastalBeachProfile profile(long seed) {
        return new CoastalBeachProfile(SEA,new CoastalTerrainPlanner(seed),
            new CoastalShape.Options(false,false,false,true,1));
    }
    @Test void beachesRetainTheMainCliffBeyondTheNarrowToe() {
        var profile=profile(19);
        for(int z=-384;z<=384;z+=8) {
            var cliff=profile.sample(32,z,new CoastalShape.Ground(150,1,32,48,150,false));
            assertTrue(Math.abs(cliff.surface()-150)<.7,"a beach cannot consume the inland cliff body");
            assertEquals(0,cliff.sand());
        }
    }
    @Test void selectedBeachesHaveShallowWaterThatDeepensGraduallyOffshore() {
        var profile=profile(19);int selected=Integer.MIN_VALUE;
        for(int z=-2048;z<=2048;z+=8) {
            boolean beach=true;
            for(int x=0;x>=-32;x-=4)if(profile.field(x,z)<.99) {beach=false;break;}
            if(beach) {selected=z;break;}
        }
        assertNotEquals(Integer.MIN_VALUE,selected,"a complete beach transect is selected");
        double previous=Double.POSITIVE_INFINITY;
        for(int distance=4;distance<=32;distance+=2) {
            var s=profile.sample(-distance,selected,new CoastalShape.Ground(20,1,-distance,48,120,true));
            assertTrue(s.surface()<=previous+.15,"small coastal variation does not reverse the seabed slope");
            if(Double.isFinite(previous))assertTrue(previous-s.surface()<1.3,"no sudden underwater cliff at the beach front");
            previous=s.surface();
        }
        assertTrue(profile.sample(-16,selected,new CoastalShape.Ground(20,1,-16,48,120,true)).surface()>SEA-3);
        assertTrue(previous<SEA-3);
    }
    @Test void oceanExtensionsLeaveElevatedOceanLandAndDistantOceanAlone() {
        var profile=profile(19);
        for(int z=-384;z<=384;z+=8) {
            assertEquals(120,profile.sample(-8,z,new CoastalShape.Ground(120,1,-8,48,140,true)).surface());
            assertEquals(20,profile.sample(-80,z,new CoastalShape.Ground(20,0,-80,48,140,true)).surface());
        }
    }
    @Test void fieldAndProfileAreContinuousAcrossChunkBoundariesAndRepeatFromTheSeed() {
        var profile=profile(42);
        var again=profile(42);
        for(int x=-256;x<256;x++) {
            var a=profile.sample(x,23,new CoastalShape.Ground(64,1,3,48,120,false));
            var b=profile.sample(x+1,23,new CoastalShape.Ground(64,1,3,48,120,false));
            assertEquals(a,again.sample(x,23,new CoastalShape.Ground(64,1,3,48,120,false)));
            assertTrue(Math.abs(a.surface()-b.surface())<.15);
            assertTrue(Math.abs(a.sand()-b.sand())<.1);
        }
    }
}

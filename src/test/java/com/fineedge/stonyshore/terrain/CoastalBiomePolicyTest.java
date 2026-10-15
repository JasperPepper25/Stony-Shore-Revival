package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.fineedge.stonyshore.terrain.CoastalBiomePolicy.Choice.*;

class CoastalBiomePolicyTest {
    private static CoastalBiomePolicy.Context context(double original,double surface,double mask,double sand,
                                                       boolean pool,boolean volume,boolean protectedBiome,boolean cold) {
        return new CoastalBiomePolicy.Context(original,surface,mask,sand,pool,volume,protectedBiome,cold);
    }
    @Test void expandsRockySurfaceIntoThePhysicalCorridorButRetainsInlandAndDeepBiomes() {
        assertEquals(ROCKY_SHORE,CoastalBiomePolicy.choose(context(130,118,1,0,false,false,false,false),120,63));
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(130,118,.3,0,false,false,false,false),120,63));
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(130,118,1,0,false,false,false,false),108,63));
    }
    @Test void dryBeachesRespectRegionalTemperatureAndSubmergedShelvesKeepOceanVariants() {
        assertEquals(BEACH,CoastalBiomePolicy.choose(context(30,65,1,.9,false,false,false,false),64,63));
        assertEquals(SNOWY_BEACH,CoastalBiomePolicy.choose(context(30,65,1,.9,false,false,false,true),64,63));
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(30,59,1,.9,false,false,false,false),64,63));
    }
    @Test void poolRimsAndRaisedRockVolumesRemainRockyWithoutRelabellingDeepFoundations() {
        assertEquals(ROCKY_SHORE,CoastalBiomePolicy.choose(context(90,90,1,.9,true,false,false,false),88,63));
        assertEquals(ROCKY_SHORE,CoastalBiomePolicy.choose(context(30,110,1,.9,false,true,false,false),70,63));
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(30,110,1,.9,false,true,false,false),48,63));
    }
    @Test void riverAndCaveProtectionAlwaysWins() {
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(30,65,1,.9,false,false,true,false),64,63));
        assertEquals(ORIGINAL,CoastalBiomePolicy.choose(context(100,115,1,0,false,true,true,false),110,63));
    }
}

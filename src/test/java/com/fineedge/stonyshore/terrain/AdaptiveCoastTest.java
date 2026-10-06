package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveCoastTest {
    private static CoastalShape coast(double height,double distance) {
        return new CoastalShape(123,63,(x,z)->new CoastalShape.Ground(height,1,distance),
            new CoastalShape.Options(true,true,true,true,.65));
    }
    @Test void lowCoastsNeverCreateTallLandforms() {
        var coast=coast(66,24);
        for(int x=-192;x<=192;x+=8)for(int z=-192;z<=192;z+=8) {
            var c=coast.column(x,z);assertNull(c.arch());assertNull(c.overhang());
            assertTrue(c.surface()<71);assertEquals("low",coast.profile(x,z).type(63));
        }
    }
    @Test void beachesCannotFlattenTallInlandCliffs() {
        var coast=coast(130,48);
        for(int x=-96;x<=96;x+=8)for(int z=-96;z<=96;z+=8) {
            var c=coast.column(x,z);assertTrue(c.surface()>105,"tall coast retains most original relief");
            assertEquals(0,c.sand(),1e-9);
        }
    }
    @Test void highCliffsStillHaveALowToeAtTheOcean() {
        var coast=coast(130,4);
        assertTrue(coast.column(0,0).surface()<69);
    }
    @Test void originalShallowCaveAirIsNeverFilledOutsidePoolLiners() {
        var coast=coast(130,48);
        for(int x=-64;x<=64;x+=8)for(int z=-64;z<=64;z+=8)for(int y=51;y<=95;y+=4) {
            var c=coast.column(x,z);
            if(c.pool()==null || y<c.surface()-2.5 || y>c.surface()+.5)
                assertTrue(coast.density(-.3,x,y,z,.15)<=-.3+1e-9);
        }
    }
    @Test void neighborhoodStatsKeepHeightReliefAndSlopeSeparateAndCrossCellsSmoothly() {
        var profiles=new CoastalProfile((x,z)->90+x*.2);
        var p=profiles.sample(0,0);assertEquals(90,p.median(),1e-9);
        assertEquals(.2,p.slope(),1e-9);assertTrue(p.relief()>0);
        assertTrue(Math.abs(profiles.sample(31,0).cliffWeight(63)-profiles.sample(32,0).cliffWeight(63))<.02);
    }
    @Test void inlandHeightTransitionsFadeMoreGraduallyThanOceanEdges() {
        var model=new NativeCoastalModel(42,63,192,(x,y,z)->(130-y)*.15,(x,z)->x>=0,(x,z)->false,
            (x,y,z)->x>=0,new CoastalShape.Options(false,false,false,false,0));
        assertEquals(0,model.ground(0,24).mask());
        assertTrue(model.ground(24,24).mask()<.7);
        assertEquals(1,model.ground(64,24).mask());
        assertEquals(.2,model.cap(.2,-1,90,24,.15));
    }
    @Test void archesDoNotOpenInsideTheBiomeTransitionBand() {
        var coast=new CoastalShape(123,63,(x,z)->new CoastalShape.Ground(130,.95,24),
            new CoastalShape.Options(true,false,true,false,0));
        for(int x=-192;x<=192;x+=16)for(int z=-192;z<=192;z+=16)
            assertNull(coast.column(x,z).arch());
        assertTrue(coast.stats().getOrDefault("archCandidates",0L)>0);
        assertTrue(coast.stats().getOrDefault("archesRejectedSupport",0L)>0);
        assertEquals(0L,coast.stats().getOrDefault("archesAccepted",0L));
    }
}

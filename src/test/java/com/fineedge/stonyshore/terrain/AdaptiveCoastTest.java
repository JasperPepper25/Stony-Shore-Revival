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
    @Test void configuredInlandAndOceanMarginsFadeContinuouslyAndPreserveTheirNeighbors() {
        CoastalColumnSampler.Terrain terrain=(x,y,z)->((x>=0?130:45)-y)*.15;
        var model=new NativeCoastalModel(42,63,192,terrain,(x,z)->x>=0,(x,z)->x<0,
            (x,y,z)->x>=0,new CoastalShape.Options(false,false,false,false,0),terrain,160);
        assertEquals(1,model.ground(96,24).mask(),"a configured wider inland corridor retains full coastal influence");
        assertTrue(model.ground(136,24).mask()>0 && model.ground(136,24).mask()<1);
        assertTrue(model.ground(-92,24).mask()>0 && model.ground(-92,24).mask()<1);
        for(int x=112;x<=176;x++)assertTrue(Math.abs(model.ground(x+1,24).mask()-model.ground(x,24).mask())<.1);
        for(int x=-128;x<=-48;x++)assertTrue(Math.abs(model.ground(x+1,24).mask()-model.ground(x,24).mask())<.1);
        assertEquals(0,model.ground(176,24).mask());assertEquals(0,model.ground(-128,24).mask());
        assertEquals(.2,model.cap(.2,176,90,24,.15));
        assertEquals(.2,model.cap(.2,-128,90,24,.15));
    }
    @Test void archesDoNotOpenInsideTheWeakInlandTransitionBand() {
        var coast=new CoastalShape(123,63,(x,z)->new CoastalShape.Ground(130,.2,24,2),
            new CoastalShape.Options(true,false,true,false,0));
        for(int x=-192;x<=192;x+=16)for(int z=-192;z<=192;z+=16)
            assertNull(coast.column(x,z).arch());

        assertEquals(0L,coast.stats().getOrDefault("archesAccepted",0L));
    }
}

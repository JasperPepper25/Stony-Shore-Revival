package com.fineedge.stonyshore.terrain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegionalCoastTest {
    private static CoastalColumnSampler coast(long seed) {
        return new CoastalColumnSampler(seed,63,(x,y,z)->(110.5-y)*.15,(x,z)->x>=0,true,(x,z)->x<0,true,false,192);
    }
    @Test void upperCoastHasMultipleTerracesAndCarvesOnlyStonyShore() {
        var c=coast(42);
        assertTrue(c.column(12,0).surface()<70);
        assertTrue(c.column(32,0).surface()>75 && c.column(32,0).surface()<85);
        assertTrue(c.column(52,0).surface()>90 && c.column(52,0).surface()<100);
        for(int x=-16;x<=192;x+=4) {
            var p=c.column(x,0);
            assertTrue(p.surface()<=110);
            if(x<0)assertEquals(1.25,c.cap(1.25,x,80,0,.15));
        }
        var stacked=new CoastalColumnSampler(42,63,(x,y,z)->(110.5-y)*.15,(x,z)->true,true,(x,z)->x<0,true,true,192,(x,y,z)->false);
        assertFalse(stacked.column(12,0).active());assertEquals(1.25,stacked.cap(1.25,12,80,0,.15));
    }
    @Test void resultsDoNotDependOnTraversalAndSandFadesUnderwater() {
        var a=coast(42);var b=coast(42);
        for(int x=128;x>=-16;x-=4)b.column(x,8);
        for(int x=-16;x<=128;x+=4)assertEquals(a.column(x,8),b.column(x,8));
        double best=0;
        for(int z=-512;z<512;z+=8) {
            double shallow=a.sandCover(-4,z,61),deep=a.sandCover(-4,z,55);
            best=Math.max(best,shallow);assertTrue(deep<=shallow);
            assertEquals(0,a.sandCover(-4,z,51));
            assertEquals(0,a.sandCover(-32,z,61));
        }
        assertTrue(best>0);
    }
}

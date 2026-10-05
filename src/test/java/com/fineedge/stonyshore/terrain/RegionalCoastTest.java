package com.fineedge.stonyshore.terrain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RegionalCoastTest {
    private static CoastalColumnSampler coast(long seed) {
        return new CoastalColumnSampler(seed,63,(x,y,z)->(110.5-y)*.15,(x,z)->x>=0,true,(x,z)->x<0,true,false,192);
    }
    @Test void upperCoastHasMultipleTerracesAndCarvesOnlyStonyShore() {
        var c=coast(42);
        java.util.Set<Integer> waterLevels=new java.util.HashSet<>();int wet=0;
        for(int x=4;x<192;x+=3)for(int z=-256;z<256;z+=3) {
            var p=c.column(x,z);if(p.waterLevel()>63) {waterLevels.add(p.waterLevel());wet++;}
        }
        assertTrue(waterLevels.size()>=4,"Elevated shelves must not share one global height band");
        assertTrue(wet>20,"A representative broad coast must contain elevated basins");
        for(int x=-16;x<=192;x+=4) {
            var p=c.column(x,0);
            assertTrue(p.surface()<=110);
            if(x<0)assertEquals(1.25,c.cap(1.25,x,80,0,.15));
        }
        var stacked=new CoastalColumnSampler(42,63,(x,y,z)->(110.5-y)*.15,(x,z)->true,true,(x,z)->x<0,true,true,192,(x,y,z)->false);
        assertFalse(stacked.column(12,0).active());assertEquals(1.25,stacked.cap(1.25,12,80,0,.15));
    }
    @Test void aHighPoolCannotRaiseWaterElsewhereInItsRegion() {
        var c=coast(42);
        for(int x=0;x<128;x+=2)for(int z=-128;z<128;z+=2) {
            var p=c.column(x,z);
            if(p.waterLevel()<=63)continue;
            // All four cell corners lie beyond every localized basin footprint.
            int cx=Math.floorDiv(x,64)*64,cz=Math.floorDiv(z,64)*64;
            for(int[] d:new int[][]{{0,0},{63,0},{0,63},{63,63}})
                assertEquals(63,c.column(cx+d[0],cz+d[1]).waterLevel());
        }
    }
    @Test void thinUpperRockCannotReappearAboveCarving() {
        var c=new CoastalColumnSampler(42,63,(x,y,z)->y==150?0.05:(110.5-y)*.15,
            (x,z)->x>=0,true,(x,z)->x<0,true,false,192);
        for(int x=4;x<=16;x+=4) {
            var p=c.column(x,0);assertTrue(p.active());assertTrue(p.surface()<145);
            assertTrue(c.cap(0.05,x,150,0,.15)<0);
        }
    }
    @Test void resultsDoNotDependOnTraversalAndSandFadesUnderwater() {
        var a=coast(42);var b=coast(42);
        for(int x=128;x>=-16;x-=4)b.column(x,8);
        for(int x=-16;x<=128;x+=4)assertEquals(a.column(x,8),b.column(x,8));
        double best=0;
        for(int z=-512;z<512;z+=8) {
            double shallow=a.sandCover(-4,z,61),deep=a.sandCover(-4,z,55);
            best=Math.max(best,shallow);assertTrue(deep<=shallow);
            assertEquals(0,a.sandCover(-4,z,38));
            assertEquals(0,a.sandCover(-40,z,61));
        }
        assertTrue(best>0);
    }
}

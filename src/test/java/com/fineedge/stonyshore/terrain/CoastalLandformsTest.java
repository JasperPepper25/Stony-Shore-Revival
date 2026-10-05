package com.fineedge.stonyshore.terrain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoastalLandformsTest {
    private static CoastalLandforms flat(long seed,CoastalColumnSampler.Terrain density) {
        return new CoastalLandforms(seed,63,(x,z)->new CoastalLandforms.Base(110,110,1,0),density,(x,z)->true,false);
    }
    @Test void elevatedPoolsHaveSolidRimsAndOneWaterPlane() {
        int count=0;
        for(int seed=0;seed<12;seed++) {
            var plans=flat(seed,(x,y,z)->(110.5-y)*.15);
            var p=plans.pool(48,48); if(p==null) continue;
            count++; assertTrue(p.water()>=104 && p.water()<=108);
            assertTrue(plans.poolFloor(p,p.x(),p.z(),110)<p.water()-2);
            for(int x=p.x()-24;x<=p.x()+24;x++) for(int z=p.z()-24;z<=p.z()+24;z++) {
                double floor=plans.poolFloor(p,x,z,110);
                if(Math.abs(x-p.x())==24 || Math.abs(z-p.z())==24)
                    assertEquals(110,floor); // Complete untouched outer rim.
                assertTrue(floor>=p.water()-p.depth()-0.5);
                assertTrue(floor<=110);
            }
        }
        assertTrue(count>0);
    }
    @Test void cavePuncturedFloorsAndLowSitesRejectWholePool() {
        for(int seed=0;seed<12;seed++) {
            assertNull(flat(seed,(x,y,z)-> y==103?-1:(110.5-y)*.15).pool(48,48));
            var low=new CoastalLandforms(seed,63,(x,z)->new CoastalLandforms.Base(65,65,1,0),(x,y,z)->65.5-y,(x,z)->true,false);
            assertNull(low.pool(48,48));
        }
    }
    @Test void archesRequireOpenPortalsAndSupportedPiers() {
        int accepted=0;
        for(int seed=0;seed<40;seed++) {
            var plans=new CoastalLandforms(seed,63,(x,z)->new CoastalLandforms.Base(70,Math.abs(z-32)<10?115:62,1,0),
                (x,y,z)->(Math.abs(z-32)<10?115.5:62.5)-y,(x,z)->true,true);
            var a=plans.arch(32,32);
            if(a!=null) {accepted++;assertTrue(a.opening(a.x(),70,a.z())<0);}
            var cliff=new CoastalLandforms(seed,63,(x,z)->new CoastalLandforms.Base(110,110,1,0),
                (x,y,z)->110.5-y,(x,z)->true,true);
            assertNull(cliff.arch(48,48)); // A blind tunnel is not an arch.
        }
        assertTrue(accepted>0);
    }
    @Test void oneSidedCliffsCanProduceSupportedOverhangs() {
        int accepted=0;
        for(int seed=0;seed<40;seed++) {
            var plans=new CoastalLandforms(seed,63,(x,z)->new CoastalLandforms.Base(x<32?70:120,x<32?70:120,1,0),
                (x,y,z)->(x<32?70.5:120.5)-y,(x,z)->true,false);
            var overhang=plans.overhang(32,32);
            if(overhang!=null) {
                accepted++;assertTrue(overhang.opening(overhang.x(),overhang.floor()+5,overhang.z())<0);
                assertTrue(overhang.opening(overhang.x(),overhang.floor()+18,overhang.z())>0);
            }
        }
        assertTrue(accepted>0);
    }
    @Test void neighboringCellsAndNegativeCoordinatesKeepCompleteFootprints() {
        var plans=flat(42,(x,y,z)->110.5-y);
        for(int cx=-3;cx<=3;cx++) for(int cz=-3;cz<=3;cz++) {
            var p=plans.pool(cx*64+32,cz*64+32);if(p==null)continue;
            for(int x=p.x()-24;x<=p.x()+24;x++)for(int z=p.z()-24;z<=p.z()+24;z++)
                if(plans.poolFloor(p,x,z,110)<109.999) {
                    assertEquals(cx,Math.floorDiv(x,64));assertEquals(cz,Math.floorDiv(z,64));
                }
        }
    }
}

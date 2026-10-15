package com.fineedge.stonyshore.generation;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SandyPoolPassTest {
    @Test void poolFinishingRequiresTheEntireDryRimAndSolidBed() {
        var shape=new SandyPoolPass.Shape(3.2,2.8,.73,19);
        Set<String> checked=new HashSet<>();
        assertTrue(SandyPoolPass.validBasin(shape,63,2,(dx,dz,floor)->{
            checked.add(dx+","+dz);return true;
        }));
        assertTrue(checked.contains("0,0"));
        int rimX=0,rimZ=0;
        outer: for(int dx=-shape.extent();dx<=shape.extent();dx++)for(int dz=-shape.extent();dz<=shape.extent();dz++)
            if(shape.rim(dx,dz)) {rimX=dx;rimZ=dz;break outer;}
        final int holeX=rimX,holeZ=rimZ;
        assertTrue(shape.rim(holeX,holeZ));
        assertTrue(checked.contains(holeX+","+holeZ),"containment includes the complete dry rim");
        assertFalse(SandyPoolPass.validBasin(shape,63,2,(dx,dz,floor)->dx!=holeX || dz!=holeZ),
            "one open rim column must reject the whole pool before any writes");
        assertFalse(SandyPoolPass.validBasin(shape,63,2,(dx,dz,floor)->dx!=0 || dz!=0),
            "an unsupported center rejects the entire pool");
    }
    @Test void allPoolWaterFacesHaveADryRimAndEveryFootprintStaysInsideItsChunk() {
        for(int seed=0;seed<200;seed++) {
            var shape=new SandyPoolPass.Shape(2+(seed%17)/16.0*1.2,1.8+(seed%13)/12.0,
                seed*.31,seed*982451653L);
            assertTrue(SandyPoolPass.validBasin(shape,63,2,(dx,dz,floor)->{
                for(int center:new int[]{5,10}) {
                    assertTrue(center+dx>=0 && center+dx<16);
                    assertTrue(center+dz>=0 && center+dz<16);
                }
                return true;
            }));
            for(int dx=-shape.extent();dx<=shape.extent();dx++)for(int dz=-shape.extent();dz<=shape.extent();dz++) {
                if(!shape.wet(dx,dz))continue;
                assertTrue(shape.floor(63,2,dx,dz)>=61 && shape.floor(63,2,dx,dz)<=62);
                for(int[] face:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                    int x=dx+face[0],z=dz+face[1];
                    assertTrue(shape.wet(x,z) || shape.rim(x,z),"each water face borders water or a checked solid rim");
                    assertTrue(Math.abs(x)<=shape.extent() && Math.abs(z)<=shape.extent());
                }
            }
        }
    }
    @Test void tooSmallBasinsAreRejectedAndShoreFeaturesHaveDisjointOwnership() {
        assertFalse(SandyPoolPass.validBasin(new SandyPoolPass.Shape(.5,.5,0,0),63,1,(dx,dz,floor)->true));
        for(boolean stony:new boolean[]{false,true}) {
            assertNotEquals(ShoreDecorationPass.ownsStoredBiome(stony,false),ShoreDecorationPass.ownsStoredBiome(stony,true));
            assertEquals(stony,ShoreDecorationPass.ownsStoredBiome(stony,false));
        }
    }
}
